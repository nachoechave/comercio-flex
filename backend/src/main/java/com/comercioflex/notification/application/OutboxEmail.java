package com.comercioflex.notification.application;

public record OutboxEmail(long id, String eventKey, String eventType,
		int attemptCount, TransactionalEmail email) {
}
