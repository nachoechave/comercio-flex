package com.comercioflex.notification.infrastructure;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.NotificationQueuedEvent;
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

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void afterCommit(NotificationQueuedEvent event) {
		trySend(event.eventKey());
	}

	public void trySend(String eventKey) {
		if (!properties.isEnabled()) return;
		Optional<OutboxEmail> claimed = requiresNew.execute(status -> outbox.claim(eventKey));
		if (claimed == null || claimed.isEmpty()) return;
		OutboxEmail message = claimed.get();
		try {
			sender.send(message.email());
			requiresNew.executeWithoutResult(status -> outbox.markSent(message.id(), clock.instant()));
		}
		catch (RuntimeException exception) {
			requiresNew.executeWithoutResult(status ->
				outbox.markFailed(message.id(), safeMessage(exception)));
			LOGGER.warn("Transactional email delivery failed eventKey={}", eventKey, exception);
		}
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
