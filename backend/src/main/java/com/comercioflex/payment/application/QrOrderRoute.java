package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record QrOrderRoute(
	long internalId,
	long tenantId,
	String tenantSlug,
	String tenantDatabaseKey,
	PaymentEnvironment environment,
	UUID paymentAttemptId,
	String providerOrderId,
	String expectedSellerAccountId,
	String status,
	int attemptCount,
	Instant expiresAt) {
}
