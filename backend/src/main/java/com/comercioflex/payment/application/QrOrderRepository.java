package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;

public interface QrOrderRepository {

	Optional<CheckoutOrder> lockOrder(UUID orderId, byte[] lookupTokenHash);

	Optional<StoredQrOrderAttempt> findByIdempotencyKey(UUID idempotencyKey);

	Optional<StoredQrOrderAttempt> findCurrentByOrder(UUID orderId, byte[] lookupTokenHash);

	Optional<StoredQrOrderAttempt> findByPublicId(UUID paymentAttemptId, boolean forUpdate);

	boolean hasBlockingIntent(long orderInternalId);

	int nextAttemptNumber(long orderInternalId);

	void insert(
		UUID paymentAttemptId,
		long orderInternalId,
		UUID idempotencyKey,
		byte[] fingerprint,
		UUID transitionIdempotencyKey,
		int attemptNumber,
		BigDecimal amount,
		String currencyCode,
		String externalReference,
		UUID providerIdempotencyKey,
		Instant providerExpiresAt,
		String sellerAccountId,
		PaymentEnvironment environment,
		String externalPosId,
		Instant now);

	boolean claimCreation(StoredQrOrderAttempt attempt, Instant now, Instant staleBefore);

	void attachProviderOrder(
		StoredQrOrderAttempt attempt,
		String providerOrderId,
		String qrData,
		String providerStatus,
		Instant providerExpiresAt,
		Instant now);

	void markCreationFailed(StoredQrOrderAttempt attempt, Instant now);

	void updateProviderStatus(
		StoredQrOrderAttempt attempt,
		String providerStatus,
		Instant now);

	void updateIntentStatus(
		StoredQrOrderAttempt attempt,
		PaymentIntentStatus target,
		Instant now);
}
