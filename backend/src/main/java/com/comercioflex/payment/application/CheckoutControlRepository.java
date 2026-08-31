package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface CheckoutControlRepository {

	void requireCommerciallyEnabled(long tenantId, PaymentEnvironment environment);

	boolean isCommerciallyEnabled(long tenantId, PaymentEnvironment environment);

	void insertRoute(
		UUID routeId,
		byte[] routeTokenHash,
		long tenantId,
		PaymentEnvironment environment,
		UUID paymentAttemptId,
		String expectedSellerAccountId,
		Instant expiresAt);

	void activateRoute(UUID paymentAttemptId, PaymentEnvironment environment, String preferenceId);

	Optional<Boolean> routePreferenceMatches(
		long tenantId, UUID paymentAttemptId,
		PaymentEnvironment environment, String preferenceId);

	void expireRoute(UUID paymentAttemptId, PaymentEnvironment environment);

	Optional<CheckoutRoute> findRoute(byte[] routeTokenHash, PaymentEnvironment environment);

	boolean insertWebhook(CheckoutRoute route, ReceivedWebhook webhook, Instant now);

	Optional<ClaimedWebhookEvent> claimNext(Instant now, Instant leasedUntil);

	boolean markProcessed(long eventId, int expectedAttemptCount, Instant now);

	boolean markFailed(
		long eventId, int expectedAttemptCount, boolean dead,
		String errorCode, Instant availableAt);

	List<FailedWebhookEvent> findDeadWebhooks(
		long tenantId, PaymentEnvironment environment, int limit);

	WebhookRetryOutcome retryWebhook(
		long tenantId, PaymentEnvironment environment,
		UUID eventPublicId, long actorUserId,
		UUID actorUserPublicId, Instant now);
}
