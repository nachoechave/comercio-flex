package com.comercioflex.payment.application;

import java.time.Instant;

public record WebhookRetryOutcome(WebhookRetryResult result, Instant availableAt) {
}
