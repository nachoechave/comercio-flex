package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record CheckoutPreferenceCommand(
	UUID paymentAttemptId,
	String providerIdempotencyKey,
	String externalReference,
	String title,
	BigDecimal amount,
	String currencyCode,
	URI returnUri,
	URI notificationUri,
	Instant expiresAt) {
}
