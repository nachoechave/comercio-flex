package com.comercioflex.notification.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository {
	boolean enqueue(String eventKey, String eventType, UUID orderId,
		Long bankTransferInternalId, TransactionalEmail email);
	Optional<OutboxEmail> claimNext(Instant eligibleAt, Instant staleSendingBefore,
		int maxAttempts);
	int recoverExhaustedStaleSending(Instant staleSendingBefore, int maxAttempts, int limit);
	boolean markSent(long id, int attemptCount, Instant sentAt);
	boolean markFailed(long id, int attemptCount, String error, Instant nextAttemptAt);
	boolean makeEligibleForManualRetry(long id, Instant eligibleAt);
}
