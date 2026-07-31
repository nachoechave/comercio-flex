package com.comercioflex.payment.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record CheckoutInitiation(
	URI checkoutUrl,
	UUID paymentAttemptId,
	Instant expiresAt,
	boolean replayed) {
}
