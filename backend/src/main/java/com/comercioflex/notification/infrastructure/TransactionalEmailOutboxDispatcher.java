package com.comercioflex.notification.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.OutboxEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;

@Component
public class TransactionalEmailOutboxDispatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalEmailOutboxDispatcher.class);

	private final NotificationOutboxRepository outbox;
	private final TransactionalEmailSender sender;
	private final EmailProperties properties;
	private final TransactionTemplate requiresNew;
	private final Clock clock;

	@Autowired
	TransactionalEmailOutboxDispatcher(NotificationOutboxRepository outbox,
			TransactionalEmailSender sender, EmailProperties properties,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactions) {
		this(outbox, sender, properties, requiresNew(transactions), Clock.systemUTC());
	}

	TransactionalEmailOutboxDispatcher(NotificationOutboxRepository outbox,
			TransactionalEmailSender sender, EmailProperties properties,
			TransactionTemplate requiresNew, Clock clock) {
		this.outbox = outbox;
		this.sender = sender;
		this.properties = properties;
		this.requiresNew = requiresNew;
		this.clock = clock;
	}

	public void deliver(OutboxEmail message) {
		try {
			sender.send(message.email());
			Boolean changed = requiresNew.execute(status ->
				outbox.markSent(message.id(), message.attemptCount(), clock.instant()));
			if (Boolean.TRUE.equals(changed)) {
				LOGGER.info("email_outbox_sent event_type={} attempt={}",
					message.eventType(), message.attemptCount());
			}
		}
		catch (RuntimeException exception) {
			Instant failedAt = clock.instant();
			Instant nextAttemptAt = nextAttemptAt(message.attemptCount(), failedAt);
			requiresNew.executeWithoutResult(status -> outbox.markFailed(
				message.id(), message.attemptCount(), safeMessage(exception), nextAttemptAt));
			LOGGER.warn("email_outbox_failed event_type={} attempt={} next_attempt_at={} error_type={}",
				message.eventType(), message.attemptCount(), nextAttemptAt,
				exception.getClass().getSimpleName());
		}
	}

	Duration backoffForAttempt(int attemptCount) {
		long initial = Math.max(1, properties.getOutboxInitialBackoffSeconds());
		long maximum = Math.max(initial, properties.getOutboxMaxBackoffSeconds());
		long delay = initial;
		for (int attempt = 1; attempt < attemptCount && delay < maximum; attempt++) {
			delay = Math.min(maximum, delay > maximum / 2 ? maximum : delay * 2);
		}
		return Duration.ofSeconds(delay);
	}

	private Instant nextAttemptAt(int attemptCount, Instant failedAt) {
		if (attemptCount >= properties.getOutboxMaxAttempts()) return null;
		return failedAt.plus(backoffForAttempt(attemptCount));
	}

	private String safeMessage(RuntimeException exception) {
		String value = exception.getMessage();
		return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
	}

	private static TransactionTemplate requiresNew(TransactionTemplate source) {
		TransactionTemplate result = new TransactionTemplate(source.getTransactionManager());
		result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return result;
	}
}
