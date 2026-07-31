package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface CheckoutControlRepository {

	void requireCommerciallyEnabled(long tenantId, PaymentEnvironment environment);

	void insertRoute(
		UUID routeId,
		byte[] routeTokenHash,
		long tenantId,
		PaymentEnvironment environment,
		UUID paymentAttemptId,
		String expectedSellerAccountId,
		Instant expiresAt);

	void activateRoute(UUID paymentAttemptId, PaymentEnvironment environment, String preferenceId);

	void expireRoute(UUID paymentAttemptId, PaymentEnvironment environment);

	Optional<CheckoutRoute> findRoute(byte[] routeTokenHash, PaymentEnvironment environment);

	boolean insertWebhook(CheckoutRoute route, ReceivedWebhook webhook, Instant now);

	Optional<ClaimedWebhookEvent> claimNext(Instant now, Instant leasedUntil);

	void markProcessed(long eventId, Instant now);

	void markFailed(long eventId, boolean dead, String errorCode, Instant availableAt);
}
