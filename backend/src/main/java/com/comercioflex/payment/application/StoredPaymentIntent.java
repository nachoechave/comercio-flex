package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentIntent;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentProvider;

public record StoredPaymentIntent(
	long internalId,
	UUID id,
	long orderInternalId,
	UUID orderId,
	UUID idempotencyKey,
	byte[] requestFingerprint,
	UUID transitionIdempotencyKey,
	PaymentProvider provider,
	PaymentIntentStatus status,
	int attemptNumber,
	BigDecimal amount,
	String currencyCode,
	String externalReference,
	long version) {

	public StoredPaymentIntent {
		requestFingerprint = requestFingerprint.clone();
	}

	@Override
	public byte[] requestFingerprint() {
		return requestFingerprint.clone();
	}

	public PaymentIntent toDomain() {
		return new PaymentIntent(
			id,
			orderId,
			provider,
			status,
			attemptNumber,
			amount,
			currencyCode,
			externalReference);
	}
}
