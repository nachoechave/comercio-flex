package com.comercioflex.notification.application;

public record OutboxEmail(long id, String eventKey, TransactionalEmail email) {
}
