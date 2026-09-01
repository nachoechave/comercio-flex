package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;

public record StoredQrOrderAttempt(
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
	int attemptNumber,
	BigDecimal amount,
	String currencyCode,
	String externalReference,
	long version,
	long qrInternalId,
	UUID providerIdempotencyKey,
	String providerOrderId,
	String qrData,
	String providerStatus,
	Instant providerExpiresAt,
	String sellerAccountId,
	PaymentEnvironment environment,
	String externalPosId,
	String creationStatus,
	Instant creationStartedAt,
	long qrVersion,
	Instant updatedAt) {

	public StoredQrOrderAttempt {
		requestFingerprint = requestFingerprint.clone();
	}

	@Override
	public byte[] requestFingerprint() {
		return requestFingerprint.clone();
	}
}
