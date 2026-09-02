package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface QrOrderControlRepository {

	void insertRoute(
		UUID publicId,
		long tenantId,
		PaymentEnvironment environment,
		UUID paymentAttemptId,
		String providerOrderId,
		String expectedSellerAccountId,
		Instant expiresAt,
		Instant now);

	Optional<QrOrderRoute> findByProviderOrderId(
		String providerOrderId, PaymentEnvironment environment);

	Optional<QrOrderRoute> claimNext(Instant now, Instant leasedUntil);

	void release(long routeId, int attemptCount, String safeErrorCode, Instant availableAt);

	void complete(long routeId, String status, Instant now);
}
