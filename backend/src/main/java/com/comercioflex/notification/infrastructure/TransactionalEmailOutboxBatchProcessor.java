package com.comercioflex.notification.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.OutboxEmail;

@Component
class TransactionalEmailOutboxBatchProcessor {
	private final NotificationOutboxRepository outbox;
	private final TransactionalEmailOutboxDispatcher dispatcher;
	private final EmailProperties properties;
	private final TransactionTemplate requiresNew;
	private final Clock clock;

	@Autowired
	TransactionalEmailOutboxBatchProcessor(NotificationOutboxRepository outbox,
			TransactionalEmailOutboxDispatcher dispatcher, EmailProperties properties,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactions) {
		this(outbox, dispatcher, properties, requiresNew(transactions), Clock.systemUTC());
	}

	TransactionalEmailOutboxBatchProcessor(NotificationOutboxRepository outbox,
			TransactionalEmailOutboxDispatcher dispatcher, EmailProperties properties,
			TransactionTemplate requiresNew, Clock clock) {
		this.outbox = outbox;
		this.dispatcher = dispatcher;
		this.properties = properties;
		this.requiresNew = requiresNew;
		this.clock = clock;
	}

	int processBatch() {
		int batchSize = positive(properties.getOutboxBatchSize(), "outbox batch size");
		int maxAttempts = positive(properties.getOutboxMaxAttempts(), "outbox max attempts");
		long sendingTimeout = positive(properties.getOutboxSendingTimeoutSeconds(),
			"outbox sending timeout");
		Instant eligibleAt = clock.instant();
		Instant staleSendingBefore = eligibleAt.minus(sendingTimeout, ChronoUnit.SECONDS);

		requiresNew.executeWithoutResult(status -> outbox.recoverExhaustedStaleSending(
			staleSendingBefore, maxAttempts, batchSize));

		int processed = 0;
		while (processed < batchSize) {
			Optional<OutboxEmail> claimed = requiresNew.execute(status ->
				outbox.claimNext(eligibleAt, staleSendingBefore, maxAttempts));
			if (claimed == null || claimed.isEmpty()) break;
			dispatcher.deliver(claimed.get());
			processed++;
		}
		return processed;
	}

	private static int positive(int value, String name) {
		if (value <= 0) throw new IllegalStateException(name + " must be greater than zero");
		return value;
	}

	private static long positive(long value, String name) {
		if (value <= 0) throw new IllegalStateException(name + " must be greater than zero");
		return value;
	}

	private static TransactionTemplate requiresNew(TransactionTemplate source) {
		TransactionTemplate result = new TransactionTemplate(source.getTransactionManager());
		result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return result;
	}
}
