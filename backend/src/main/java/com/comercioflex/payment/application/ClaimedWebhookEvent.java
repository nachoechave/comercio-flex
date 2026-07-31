package com.comercioflex.payment.application;

import java.util.UUID;

public record ClaimedWebhookEvent(
	long internalId,
	UUID publicId,
	int attemptCount,
	String providerResourceId,
	CheckoutRoute route) {
}
