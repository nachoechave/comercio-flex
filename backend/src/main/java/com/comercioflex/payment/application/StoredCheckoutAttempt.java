package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;

public record StoredCheckoutAttempt(
	long internalId,
	UUID id,
	long orderInternalId,
	UUID orderId,
	long orderNumber,
	String orderStatus,
	Instant reservationExpiresAt,
	UUID idempotencyKey,
	byte[] requestFingerprint,
	UUID transitionIdempotencyKey,
	PaymentIntentStatus status,
	BigDecimal amount,
	String currencyCode,
	String externalReference,
	Instant returnTokenExpiresAt,
	String preferenceId,
	URI checkoutUri,
	Instant checkoutExpiresAt,
	String sellerAccountId,
	PaymentEnvironment environment,
	Instant updatedAt,
	long version) {

	public StoredCheckoutAttempt {
		requestFingerprint = requestFingerprint.clone();
	}

	@Override
	public byte[] requestFingerprint() {
		return requestFingerprint.clone();
	}
}
