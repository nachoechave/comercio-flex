package com.comercioflex.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

class TransactionalEmailOutboxBatchProcessorTests {
	private static final Instant NOW = Instant.parse("2026-08-25T15:00:00Z");
	private static final Instant STALE_BEFORE = NOW.minusSeconds(300);

	@Test
	void pendingEligibleMessagesAreClaimedAndDeliveredUpToBatchSize() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailOutboxDispatcher dispatcher = mock(TransactionalEmailOutboxDispatcher.class);
		EmailProperties properties = new EmailProperties();
		properties.setOutboxBatchSize(2);
		OutboxEmail first = message(1L);
		OutboxEmail second = message(2L);
		when(outbox.claimNext(NOW, STALE_BEFORE, 5))
			.thenReturn(Optional.of(first), Optional.of(second));

		int processed = processor(outbox, dispatcher, properties).processBatch();

		assertThat(processed).isEqualTo(2);
		verify(dispatcher).deliver(first);
		verify(dispatcher).deliver(second);
		verify(outbox, org.mockito.Mockito.times(2)).claimNext(NOW, STALE_BEFORE, 5);
	}

	@Test
	void stopsWhenThereIsNoEligibleMessage() {
		NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
		TransactionalEmailOutboxDispatcher dispatcher = mock(TransactionalEmailOutboxDispatcher.class);
		EmailProperties properties = new EmailProperties();
		when(outbox.claimNext(NOW, STALE_BEFORE, 5)).thenReturn(Optional.empty());

		int processed = processor(outbox, dispatcher, properties).processBatch();

		assertThat(processed).isZero();
		verify(outbox).recoverExhaustedStaleSending(STALE_BEFORE, 5, 25);
		verify(outbox).claimNext(NOW, STALE_BEFORE, 5);
	}

	private TransactionalEmailOutboxBatchProcessor processor(NotificationOutboxRepository outbox,
			TransactionalEmailOutboxDispatcher dispatcher, EmailProperties properties) {
		return new TransactionalEmailOutboxBatchProcessor(outbox, dispatcher, properties,
			transactions(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private OutboxEmail message(long id) {
		return new OutboxEmail(id, "event-" + id, "ORDER_CONFIRMED", 1,
			new TransactionalEmail("ana@example.com", "Asunto", "<p>Hola</p>", "Hola"));
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
