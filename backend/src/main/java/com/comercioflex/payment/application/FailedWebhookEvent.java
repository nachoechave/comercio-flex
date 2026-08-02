package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

public record FailedWebhookEvent(
	UUID eventId,
	String status,
	int attemptCount,
	String safeErrorCode,
	Instant occurredAt,
	boolean retryAllowed) {
}
