package com.comercioflex.notification.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository {
	boolean enqueue(String eventKey, String eventType, UUID orderId,
		Long bankTransferInternalId, TransactionalEmail email);
	Optional<OutboxEmail> claim(String eventKey);
	void markSent(long id, Instant sentAt);
	void markFailed(long id, String error);
}
