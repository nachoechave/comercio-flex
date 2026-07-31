package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentProvider;
import com.comercioflex.payment.domain.PaymentResultStatus;

public interface PaymentRepository {

	Optional<LockedPaymentOrder> lockOrder(UUID orderId);

	Optional<StoredPaymentIntent> findByIdempotencyKey(UUID idempotencyKey);

	Optional<StoredPaymentIntent> findByPublicId(UUID paymentIntentId);

	Optional<StoredPaymentIntent> lockIntent(UUID paymentIntentId);

	boolean hasBlockingIntent(long orderInternalId);

	int nextAttemptNumber(long orderInternalId);

	long insertIntent(
		UUID paymentIntentId,
		long orderInternalId,
		UUID idempotencyKey,
		byte[] requestFingerprint,
		UUID transitionIdempotencyKey,
		PaymentProvider provider,
		int attemptNumber,
		BigDecimal amount,
		String currencyCode,
		String externalReference);

	Optional<StoredPaymentTransaction> findTransaction(
		PaymentProvider provider,
		String providerPaymentId);

	long insertTransaction(
		UUID transactionId,
		long paymentIntentInternalId,
		PaymentProvider provider,
		GatewayPayment payment);

	void updateIntentStatus(
		long paymentIntentInternalId,
		long version,
		PaymentIntentStatus expectedStatus,
		PaymentIntentStatus status);

	void markTransactionApplied(long transactionInternalId, Instant appliedAt);

	void markTransactionForReview(long transactionInternalId);
}
