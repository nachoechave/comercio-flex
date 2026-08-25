package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.notification.application.OutboxEmail;

@Testcontainers
class JdbcNotificationOutboxRepositoryIntegrationTests {
	private static final Instant NOW = Instant.parse("2026-08-25T15:00:00Z");
	private static final UUID ORDER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>(
		DockerImageName.parse("mysql:8.4.10"));

	private static DriverManagerDataSource dataSource;
	private JdbcTemplate jdbc;
	private JdbcNotificationOutboxRepository repository;
	private TransactionTemplate transactions;

	@BeforeAll
	static void migrate() {
		dataSource = new DriverManagerDataSource(
			DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
		Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration/tenant")
			.load()
			.migrate();
	}

	@AfterAll
	static void clearDataSource() {
		dataSource = null;
	}

	@BeforeEach
	void seedOrder() {
		jdbc = new JdbcTemplate(dataSource);
		repository = new JdbcNotificationOutboxRepository(jdbc);
		transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		jdbc.update("DELETE FROM transactional_email_outbox");
		jdbc.update("DELETE FROM orders");
		jdbc.update("""
			INSERT INTO orders (
				public_id, idempotency_key, request_fingerprint, lookup_token_hash,
				status, fulfillment_type, customer_name, customer_phone, customer_email,
				currency_code, subtotal, reservation_expires_at
			) VALUES (
				UUID_TO_BIN(?), UUID_TO_BIN(UUID()), UNHEX(SHA2('request', 256)),
				UNHEX(SHA2('lookup', 256)), 'PENDING_CONFIRMATION', 'PICKUP',
				'Ana', '1155551234', 'ana@example.com', 'ARS', 100.00, ?
			)
			""", ORDER_ID.toString(), java.sql.Timestamp.from(NOW.plusSeconds(3_600)));
	}

	@Test
	void pendingIsClaimedOnceAndSentIsNeverClaimedAgain() {
		insert("pending", "PENDING", 0, null, null);

		OutboxEmail claimed = claim(NOW, NOW.minusSeconds(300), 5).orElseThrow();

		assertThat(claimed.attemptCount()).isEqualTo(1);
		assertThat(status("pending")).isEqualTo("SENDING");
		assertThat(repository.markSent(claimed.id(), claimed.attemptCount(), NOW)).isTrue();
		assertThat(claim(NOW.plusSeconds(600), NOW, 5)).isEmpty();
		assertThat(status("pending")).isEqualTo("SENT");
	}

	@Test
	void failedOnlyBecomesEligibleAfterNextAttemptAndBelowMaximumAttempts() {
		insert("future", "FAILED", 1, NOW.plusSeconds(1), null);
		assertThat(claim(NOW, NOW.minusSeconds(300), 5)).isEmpty();

		jdbc.update("UPDATE transactional_email_outbox SET next_attempt_at = ? WHERE event_key = 'future'",
			java.sql.Timestamp.from(NOW.minusSeconds(1)));
		assertThat(claim(NOW, NOW.minusSeconds(300), 5).orElseThrow().attemptCount()).isEqualTo(2);

		jdbc.update("DELETE FROM transactional_email_outbox");
		insert("exhausted", "FAILED", 5, NOW.minusSeconds(1), null);
		assertThat(claim(NOW, NOW.minusSeconds(300), 5)).isEmpty();
	}

	@Test
	void staleSendingIsRecoveredButRecentSendingIsUntouched() {
		insert("recent", "SENDING", 1, null, NOW.minusSeconds(299));
		assertThat(claim(NOW, NOW.minusSeconds(300), 5)).isEmpty();

		jdbc.update("UPDATE transactional_email_outbox SET sending_started_at = ? WHERE event_key = 'recent'",
			java.sql.Timestamp.from(NOW.minusSeconds(301)));
		assertThat(claim(NOW, NOW.minusSeconds(300), 5).orElseThrow().attemptCount()).isEqualTo(2);
	}

	@Test
	void exhaustedStaleSendingBecomesAuditableFailedAndCanBeManuallyReset() {
		insert("stale", "SENDING", 5, null, NOW.minusSeconds(301));

		int recovered = transactions.execute(status ->
			repository.recoverExhaustedStaleSending(NOW.minusSeconds(300), 5, 25));

		assertThat(recovered).isEqualTo(1);
		assertThat(status("stale")).isEqualTo("FAILED");
		assertThat(nextAttempt("stale")).isNull();
		long id = jdbc.queryForObject(
			"SELECT id FROM transactional_email_outbox WHERE event_key = 'stale'", Long.class);
		assertThat(repository.makeEligibleForManualRetry(id, NOW)).isTrue();
		assertThat(claim(NOW, NOW.minusSeconds(300), 5).orElseThrow().attemptCount()).isEqualTo(1);
	}

	@Test
	void concurrentWorkersCannotClaimTheSameRow() throws Exception {
		insert("single", "PENDING", 0, null, null);
		JdbcNotificationOutboxRepository secondRepository =
			new JdbcNotificationOutboxRepository(new JdbcTemplate(dataSource));
		TransactionTemplate secondTransactions =
			new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		CountDownLatch firstClaimed = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Optional<OutboxEmail>> first = executor.submit(() -> transactions.execute(status -> {
				Optional<OutboxEmail> result = repository.claimNext(NOW, NOW.minusSeconds(300), 5);
				firstClaimed.countDown();
				await(releaseFirst);
				return result;
			}));
			assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Optional<OutboxEmail>> second = executor.submit(() -> secondTransactions.execute(
				status -> secondRepository.claimNext(NOW, NOW.minusSeconds(300), 5)));

			Optional<OutboxEmail> secondClaim = second.get(5, TimeUnit.SECONDS);
			releaseFirst.countDown();
			Optional<OutboxEmail> firstClaim = first.get(5, TimeUnit.SECONDS);

			assertThat(firstClaim).isPresent();
			assertThat(secondClaim).isEmpty();
		}
		finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	private Optional<OutboxEmail> claim(Instant eligibleAt, Instant staleBefore, int maxAttempts) {
		return transactions.execute(status ->
			repository.claimNext(eligibleAt, staleBefore, maxAttempts));
	}

	private void insert(String eventKey, String status, int attempts,
			Instant nextAttemptAt, Instant sendingStartedAt) {
		jdbc.update("""
			INSERT INTO transactional_email_outbox (
				event_key, event_type, order_id, recipient, subject, html_body, text_body,
				status, attempt_count, next_attempt_at, sending_started_at
			) SELECT ?, 'ORDER_CONFIRMED', id, 'ana@example.com', 'Asunto', '<p>Hola</p>',
				'Hola', ?, ?, ?, ? FROM orders WHERE public_id = UUID_TO_BIN(?)
			""", eventKey, status, attempts, timestamp(nextAttemptAt), timestamp(sendingStartedAt),
			ORDER_ID.toString());
	}

	private String status(String eventKey) {
		return jdbc.queryForObject(
			"SELECT status FROM transactional_email_outbox WHERE event_key = ?",
			String.class, eventKey);
	}

	private java.sql.Timestamp nextAttempt(String eventKey) {
		return jdbc.queryForObject(
			"SELECT next_attempt_at FROM transactional_email_outbox WHERE event_key = ?",
			(rs, row) -> rs.getTimestamp(1), eventKey);
	}

	private java.sql.Timestamp timestamp(Instant value) {
		return value == null ? null : java.sql.Timestamp.from(value);
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for concurrent claim test");
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while testing concurrent claim", exception);
		}
	}
}
