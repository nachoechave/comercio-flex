package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.OutboxEmail;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;

class TransactionalEmailOutboxDispatcherTests {
	private static final Instant NOW = Instant.parse("2026-08-25T15:00:00Z");

	@Test
	void successfulDeliveryIsSentOutsideTheDatabaseTransactionAndMarkedSentAfterwards() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		TrackingTransactionManager manager = new TrackingTransactionManager();
		TransactionalEmail email = email();
		doAnswer(invocation -> {
			assertThat(manager.isActive()).isFalse();
			return null;
		}).when(sender).send(email);

		new TransactionalEmailOutboxDispatcher(outbox, sender, properties(),
			new TransactionTemplate(manager), Clock.fixed(NOW, ZoneOffset.UTC))
			.deliver(new OutboxEmail(5L, "event", "ORDER_CONFIRMED", 1, email));

		verify(sender).send(email);
		verify(outbox).markSent(5L, 1, NOW);
		verify(outbox, never()).markFailed(any(Long.class), any(Integer.class), any(), any());
	}

	@Test
	void smtpFailureIsRecordedWithItsNextAttemptWithoutEscapingTheWorker() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		TransactionalEmail email = email();
		doThrow(new IllegalStateException("SMTP caído")).when(sender).send(email);

		assertThatCode(() -> new TransactionalEmailOutboxDispatcher(outbox, sender, properties(),
			transactions(), Clock.fixed(NOW, ZoneOffset.UTC))
			.deliver(new OutboxEmail(5L, "event", "ORDER_CONFIRMED", 1, email)))
			.doesNotThrowAnyException();

		verify(outbox).markFailed(5L, 1, "SMTP caído", NOW.plusSeconds(60));
		verify(outbox, never()).markSent(any(Long.class), any(Integer.class), any());
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void smtpFailureLogsErrorTypeWithoutExposingSensitiveDetails(CapturedOutput output) {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		TransactionalEmail email = email();
		doThrow(new IllegalStateException("credential-sensitive-detail"))
			.when(sender).send(email);

		new TransactionalEmailOutboxDispatcher(outbox, sender, properties(),
			transactions(), Clock.fixed(NOW, ZoneOffset.UTC))
			.deliver(new OutboxEmail(5L, "event", "ORDER_CONFIRMED", 1, email));

		assertThat(output.getAll())
			.contains("error_type=IllegalStateException")
			.doesNotContain("credential-sensitive-detail");
	}

	@Test
	void backoffDoublesAndStopsAtTheConfiguredMaximum() {
		EmailProperties properties = properties();
		properties.setOutboxInitialBackoffSeconds(60);
		properties.setOutboxMaxBackoffSeconds(240);
		TransactionalEmailOutboxDispatcher dispatcher = new TransactionalEmailOutboxDispatcher(
			mock(NotificationOutboxRepository.class), mock(TransactionalEmailSender.class),
			properties, transactions(), Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(dispatcher.backoffForAttempt(1)).isEqualTo(Duration.ofSeconds(60));
		assertThat(dispatcher.backoffForAttempt(2)).isEqualTo(Duration.ofSeconds(120));
		assertThat(dispatcher.backoffForAttempt(3)).isEqualTo(Duration.ofSeconds(240));
		assertThat(dispatcher.backoffForAttempt(8)).isEqualTo(Duration.ofSeconds(240));
	}

	@Test
	void exhaustedFailureHasNoAutomaticNextAttempt() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		TransactionalEmail email = email();
		doThrow(new IllegalStateException("SMTP caído")).when(sender).send(email);

		new TransactionalEmailOutboxDispatcher(outbox, sender, properties(),
			transactions(), Clock.fixed(NOW, ZoneOffset.UTC))
			.deliver(new OutboxEmail(5L, "event", "ORDER_CONFIRMED", 5, email));

		verify(outbox).markFailed(5L, 5, "SMTP caído", null);
	}

	private EmailProperties properties() {
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		return properties;
	}

	private TransactionalEmail email() {
		return new TransactionalEmail("ana@example.com", "Asunto", "<p>Hola</p>", "Hola");
	}

	private TransactionTemplate transactions() {
		return new TransactionTemplate(new TrackingTransactionManager());
	}

	private static final class TrackingTransactionManager implements PlatformTransactionManager {
		private boolean active;

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			active = true;
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
			active = false;
		}

		@Override
		public void rollback(TransactionStatus status) {
			active = false;
		}

		boolean isActive() {
			return active;
		}
	}
}
