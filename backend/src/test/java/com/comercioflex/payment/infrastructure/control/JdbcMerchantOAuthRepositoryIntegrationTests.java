package com.comercioflex.payment.infrastructure.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.payment.application.ClaimedOAuthAttempt;
import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.payment.application.MerchantOAuthRepository;
import com.comercioflex.payment.application.StoredMerchantConnection;
import com.comercioflex.payment.domain.MerchantConnectionStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Testcontainers
@SpringBootTest
class JdbcMerchantOAuthRepositoryIntegrationTests {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
	private static final UUID USER_A_PUBLIC_ID =
		UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private static final UUID USER_B_PUBLIC_ID =
		UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>(
		DockerImageName.parse("mysql:8.4.10"));

	@Autowired MerchantOAuthRepository repository;
	@Autowired DataSource controlDataSource;
	@Autowired @Qualifier("controlTransactionTemplate") TransactionTemplate transactions;

	private JdbcTemplate jdbc;
	private long tenantA;
	private long tenantB;
	private long userA;
	private long userB;

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", DATABASE::getUsername);
		registry.add("spring.datasource.password", DATABASE::getPassword);
		registry.add("spring.flyway.user", DATABASE::getUsername);
		registry.add("spring.flyway.password", DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "false");
	}

	@BeforeEach
	void seed() {
		jdbc = new JdbcTemplate(controlDataSource);
		jdbc.update("DELETE FROM merchant_payment_connection_events");
		jdbc.update("DELETE FROM merchant_payment_connections");
		jdbc.update("DELETE FROM payment_oauth_attempts");
		jdbc.update("DELETE FROM memberships");
		jdbc.update("DELETE FROM platform_users");
		jdbc.update("DELETE FROM tenants");
		jdbc.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES (UUID_TO_BIN(UUID()), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
			       (UUID_TO_BIN(UUID()), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		insertUser(USER_A_PUBLIC_ID, "owner-a@example.test", "tienda-a");
		insertUser(USER_B_PUBLIC_ID, "owner-b@example.test", "tienda-b");
		tenantA = id("SELECT id FROM tenants WHERE slug = 'tienda-a'");
		tenantB = id("SELECT id FROM tenants WHERE slug = 'tienda-b'");
		userA = id("SELECT id FROM platform_users WHERE email_normalized = 'owner-a@example.test'");
		userB = id("SELECT id FROM platform_users WHERE email_normalized = 'owner-b@example.test'");
	}

	@Test
	void emptyConnectionTableAllowsFirstSellerAndIsolatesSellerByTenantAndEnvironment() {
		assertThat(jdbc.queryForObject(
			"SELECT COUNT(*) FROM merchant_payment_connections", Integer.class)).isZero();

		connect(tenantA, userA, USER_A_PUBLIC_ID, "seller-a", PaymentEnvironment.PRODUCTION);

		StoredMerchantConnection first = repository
			.findConnection(tenantA, PaymentEnvironment.PRODUCTION, false)
			.orElseThrow();
		assertThat(first.status()).isEqualTo(MerchantConnectionStatus.CONNECTED);
		assertThat(first.sellerAccountId()).isEqualTo("seller-a");

		assertThatThrownBy(() -> connect(
			tenantB, userB, USER_B_PUBLIC_ID, "seller-a", PaymentEnvironment.PRODUCTION))
			.isInstanceOf(DataIntegrityViolationException.class);

		connect(tenantB, userB, USER_B_PUBLIC_ID, "seller-b", PaymentEnvironment.PRODUCTION);
		assertThat(repository.findConnection(tenantB, PaymentEnvironment.PRODUCTION, false))
			.get()
			.extracting(StoredMerchantConnection::sellerAccountId)
			.isEqualTo("seller-b");

		assertThat(repository.findActiveBySeller(
			"seller-a", PaymentEnvironment.TEST, false)).isEmpty();
	}

	@Test
	void stateIsBoundToUserEnvironmentAndCanBeClaimedOnlyOnce() {
		byte[] stateHash = sha256("opaque-state-a");
		UUID attemptId = UUID.randomUUID();
		repository.insertAttempt(
			attemptId, tenantA, userA, USER_A_PUBLIC_ID, PaymentEnvironment.PRODUCTION,
			stateHash, encrypted("pkce-a"), NOW.plusSeconds(600));

		assertThat(claim(stateHash, userB, PaymentEnvironment.PRODUCTION)).isEmpty();
		assertThat(claim(stateHash, userA, PaymentEnvironment.TEST)).isEmpty();

		Optional<ClaimedOAuthAttempt> claimed =
			claim(stateHash, userA, PaymentEnvironment.PRODUCTION);

		assertThat(claimed).isPresent();
		assertThat(claimed.orElseThrow().publicId()).isEqualTo(attemptId);
		assertThat(claimed.orElseThrow().tenantId()).isEqualTo(tenantA);
		assertThat(claim(stateHash, userA, PaymentEnvironment.PRODUCTION)).isEmpty();
	}

	@Test
	void expiredProcessingAttemptIsCleanedAndDoesNotBlockAReplacement() {
		byte[] staleState = sha256("stale-state");
		repository.insertAttempt(
			UUID.randomUUID(), tenantA, userA, USER_A_PUBLIC_ID, PaymentEnvironment.PRODUCTION,
			staleState, encrypted("stale-pkce"), NOW.plusSeconds(600));
		assertThat(claim(staleState, userA, PaymentEnvironment.PRODUCTION)).isPresent();

		transactions.executeWithoutResult(status -> repository.supersedePending(
			tenantA, userA, PaymentEnvironment.PRODUCTION, NOW.plusSeconds(601)));

		assertThat(jdbc.queryForObject(
			"SELECT status FROM payment_oauth_attempts WHERE state_hash = ?",
			String.class, staleState)).isEqualTo("EXPIRED");
		assertThat(jdbc.queryForObject(
			"SELECT pkce_verifier_ciphertext IS NULL FROM payment_oauth_attempts WHERE state_hash = ?",
			Boolean.class, staleState)).isTrue();

		repository.insertAttempt(
			UUID.randomUUID(), tenantA, userA, USER_A_PUBLIC_ID, PaymentEnvironment.PRODUCTION,
			sha256("replacement-state"), encrypted("replacement-pkce"), NOW.plusSeconds(1200));
		assertThat(jdbc.queryForObject("""
			SELECT COUNT(*) FROM payment_oauth_attempts
			WHERE tenant_id = ? AND environment = 'PRODUCTION' AND status = 'PENDING'
			""", Integer.class, tenantA)).isEqualTo(1);
	}

	@Test
	void enforcesGrantedScopesLengthWithMySqlErrorDetails() {
		Set<String> maximumScopes = Set.of("s".repeat(255));
		connect(
			tenantA, userA, USER_A_PUBLIC_ID, "seller-scopes-fit",
			PaymentEnvironment.PRODUCTION, maximumScopes,
			encryptedBytes(32), encryptedBytes(32));

		assertThat(jdbc.queryForObject("""
			SELECT CHAR_LENGTH(granted_scopes)
			FROM merchant_payment_connections
			WHERE tenant_id = ? AND environment = 'PRODUCTION'
			""", Integer.class, tenantA)).isEqualTo(255);

		clearConnections();
		assertLengthViolation(() -> connect(
			tenantA, userA, USER_A_PUBLIC_ID, "seller-scopes-overflow",
			PaymentEnvironment.PRODUCTION, Set.of("s".repeat(256)),
			encryptedBytes(32), encryptedBytes(32)));
	}

	@Test
	void enforcesCiphertextLengthWithMySqlErrorDetails() {
		connect(
			tenantA, userA, USER_A_PUBLIC_ID, "seller-ciphertext-fit",
			PaymentEnvironment.PRODUCTION, Set.of("read", "write", "offline_access"),
			encryptedBytes(4096), encryptedBytes(4096));

		assertThat(jdbc.queryForObject("""
			SELECT OCTET_LENGTH(access_token_ciphertext)
			FROM merchant_payment_connections
			WHERE tenant_id = ? AND environment = 'PRODUCTION'
			""", Integer.class, tenantA)).isEqualTo(4096);

		clearConnections();
		assertLengthViolation(() -> connect(
			tenantA, userA, USER_A_PUBLIC_ID, "seller-ciphertext-overflow",
			PaymentEnvironment.PRODUCTION, Set.of("read", "write", "offline_access"),
			encryptedBytes(4097), encryptedBytes(32)));
	}

	private Optional<ClaimedOAuthAttempt> claim(
			byte[] stateHash, long userId, PaymentEnvironment environment) {
		return transactions.execute(status ->
			repository.claimAttempt(stateHash, userId, environment, NOW));
	}

	private void connect(
			long tenantId,
			long userId,
			UUID userPublicId,
			String sellerAccountId,
			PaymentEnvironment environment) {
		connect(
			tenantId, userId, userPublicId, sellerAccountId, environment,
			Set.of("read", "write", "offline_access"),
			encrypted("access-token"), encrypted("refresh-token"));
	}

	private void connect(
			long tenantId,
			long userId,
			UUID userPublicId,
			String sellerAccountId,
			PaymentEnvironment environment,
			Set<String> scopes,
			EncryptedSecret accessToken,
			EncryptedSecret refreshToken) {
		repository.upsertConnected(
			UUID.randomUUID(), tenantId, environment, sellerAccountId, "SELLER",
			scopes, accessToken, refreshToken, NOW.plusSeconds(3600), userId, userPublicId,
			UUID.randomUUID(), NOW, Optional.empty());
	}

	private void clearConnections() {
		jdbc.update("DELETE FROM merchant_payment_connection_events");
		jdbc.update("DELETE FROM merchant_payment_connections");
	}

	private void assertLengthViolation(Runnable operation) {
		assertThatThrownBy(operation::run)
			.isInstanceOf(DataIntegrityViolationException.class)
			.satisfies(exception -> {
				SQLException sqlException = sqlException(exception);
				assertThat(sqlException.getSQLState()).isEqualTo("22001");
				assertThat(sqlException.getErrorCode()).isEqualTo(1406);
			});
	}

	private SQLException sqlException(Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			if (current instanceof SQLException sqlException) {
				return sqlException;
			}
			current = current.getCause();
		}
		throw new AssertionError("Expected an SQLException cause");
	}

	private void insertUser(UUID publicId, String email, String tenantSlug) {
		jdbc.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (UUID_TO_BIN(?), ?, 'Owner', '{noop}unused', 'ACTIVE')
			""", publicId.toString(), email);
		jdbc.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT platform_user.id, tenant.id, 'OWNER', 'ACTIVE'
			FROM platform_users platform_user, tenants tenant
			WHERE platform_user.email_normalized = ? AND tenant.slug = ?
			""", email, tenantSlug);
	}

	private long id(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private EncryptedSecret encrypted(String value) {
		return new EncryptedSecret(
			"v1", new byte[12], value.getBytes(StandardCharsets.UTF_8));
	}

	private EncryptedSecret encryptedBytes(int size) {
		return new EncryptedSecret("v1", new byte[12], new byte[size]);
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
