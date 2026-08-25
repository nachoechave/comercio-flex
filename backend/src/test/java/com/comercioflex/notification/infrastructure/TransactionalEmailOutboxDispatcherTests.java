package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
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
	void disabledEmailNeverClaimsOrCallsTheSender() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		EmailProperties properties = new EmailProperties();

		new TransactionalEmailOutboxDispatcher(outbox, sender, properties,
			transactions(), Clock.fixed(NOW, ZoneOffset.UTC)).trySend("event");

		verify(outbox, never()).claim(any());
		verify(sender, never()).send(any());
	}

	@Test
	void senderFailureIsRecordedWithoutEscapingToTheBusinessFlow() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailSender sender = mock(TransactionalEmailSender.class);
		EmailProperties properties = new EmailProperties();
		properties.setEnabled(true);
		TransactionalEmail email = new TransactionalEmail(
			"ana@example.com", "Asunto", "<p>Hola</p>", "Hola");
		when(outbox.claim("event")).thenReturn(Optional.of(new OutboxEmail(5L, "event", email)));
		org.mockito.Mockito.doThrow(new IllegalStateException("SMTP caído")).when(sender).send(email);

		assertThatCode(() -> new TransactionalEmailOutboxDispatcher(outbox, sender, properties,
			transactions(), Clock.fixed(NOW, ZoneOffset.UTC)).trySend("event"))
			.doesNotThrowAnyException();

		verify(outbox).markFailed(5L, "SMTP caído");
		verify(outbox, never()).markSent(any(Long.class), any());
	}

	private TransactionTemplate transactions() {
		PlatformTransactionManager manager = new PlatformTransactionManager() {
			@Override public TransactionStatus getTransaction(TransactionDefinition definition) {
				return new SimpleTransactionStatus();
			}
			@Override public void commit(TransactionStatus status) { }
			@Override public void rollback(TransactionStatus status) { }
		};
		return new TransactionTemplate(manager);
	}
}
