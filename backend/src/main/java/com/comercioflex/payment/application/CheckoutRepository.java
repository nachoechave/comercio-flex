package com.comercioflex.payment.application;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface CheckoutRepository {

	Optional<CheckoutOrder> lockOrder(UUID orderId, byte[] lookupTokenHash);

	Optional<StoredCheckoutAttempt> findByIdempotencyKey(UUID idempotencyKey);

	Optional<StoredCheckoutAttempt> findByPublicId(UUID paymentAttemptId, boolean forUpdate);

	Optional<StoredCheckoutAttempt> findByReturnTokenHash(byte[] returnTokenHash);

	Optional<StoredCheckoutAttempt> findPendingByOrder(
		UUID orderId, byte[] lookupTokenHash);

	boolean hasBlockingIntent(long orderInternalId);

	int nextAttemptNumber(long orderInternalId);

	void insertIntent(
		UUID paymentAttemptId,
		long orderInternalId,
		UUID idempotencyKey,
		byte[] fingerprint,
		UUID transitionIdempotencyKey,
		byte[] returnTokenHash,
		Instant returnTokenExpiresAt,
		int attemptNumber,
		java.math.BigDecimal amount,
		String currencyCode);

	void attachPreference(
		StoredCheckoutAttempt attempt,
		String preferenceId,
		URI checkoutUri,
		Instant expiresAt,
		String sellerAccountId,
		PaymentEnvironment environment,
		Instant now);

	void markCreationForReview(StoredCheckoutAttempt attempt);

	void applyVerifiedPayment(
		StoredCheckoutAttempt attempt,
		VerifiedProviderPayment payment,
		boolean applied,
		boolean reviewRequired,
		Instant now);

	String latestProviderStatus(long paymentIntentInternalId);
}
