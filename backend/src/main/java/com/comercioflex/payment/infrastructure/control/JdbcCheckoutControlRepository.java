package com.comercioflex.payment.infrastructure.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.payment.application.CheckoutControlRepository;
import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.CheckoutRoute;
import com.comercioflex.payment.application.ClaimedWebhookEvent;
import com.comercioflex.payment.application.FailedWebhookEvent;
import com.comercioflex.payment.application.ReceivedWebhook;
import com.comercioflex.payment.application.WebhookRetryResult;
import com.comercioflex.payment.application.WebhookRetryOutcome;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Repository
public class JdbcCheckoutControlRepository implements CheckoutControlRepository {

	private static final String ROUTE_SELECT = """
		SELECT route.id, BIN_TO_UUID(route.public_id) public_id,
			route.tenant_id, tenant.slug tenant_slug,
			tenant.database_key, route.environment,
			BIN_TO_UUID(route.payment_intent_public_id) payment_intent_public_id,
			route.expected_seller_account_id, route.provider_preference_id,
			route.status, route.expires_at
		FROM payment_webhook_routes route
		JOIN tenants tenant ON tenant.id = route.tenant_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcCheckoutControlRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void requireCommerciallyEnabled(long tenantId, PaymentEnvironment environment) {
		if (!isCommerciallyEnabled(tenantId, environment)) {
			throw new CheckoutPaymentException(
				"PAYMENTS_NOT_ENABLED", "Los pagos en línea no están habilitados para este comercio.");
		}
	}

	@Override
	public boolean isCommerciallyEnabled(long tenantId, PaymentEnvironment environment) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM merchant_payment_capabilities capability
			JOIN tenants tenant ON tenant.id = capability.tenant_id
			WHERE capability.tenant_id = ? AND capability.environment = ?
				AND capability.checkout_enabled = TRUE AND tenant.status = 'ACTIVE'
			""", Integer.class, tenantId, environment.name());
		return count != null && count == 1;
	}

	@Override
	public void insertRoute(
			UUID routeId, byte[] routeTokenHash, long tenantId,
			PaymentEnvironment environment, UUID paymentAttemptId,
			String expectedSellerAccountId, Instant expiresAt) {
		try {
			jdbcTemplate.update("""
				INSERT INTO payment_webhook_routes (
					public_id, route_token_hash, tenant_id, environment,
					payment_intent_public_id, expected_seller_account_id,
					status, expires_at
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), ?, 'PENDING', ?)
				""", routeId.toString(), routeTokenHash, tenantId, environment.name(),
				paymentAttemptId.toString(), expectedSellerAccountId, Timestamp.from(expiresAt));
		}
		catch (DuplicateKeyException exception) {
			Integer matching = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM payment_webhook_routes
				WHERE route_token_hash = ? AND tenant_id = ? AND environment = ?
					AND payment_intent_public_id = UUID_TO_BIN(?)
					AND expected_seller_account_id = ?
				""", Integer.class, routeTokenHash, tenantId, environment.name(),
				paymentAttemptId.toString(), expectedSellerAccountId);
			if (matching == null || matching != 1) {
				throw exception;
			}
		}
	}

	@Override
	public void activateRoute(
			UUID paymentAttemptId, PaymentEnvironment environment, String preferenceId) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_webhook_routes
			SET provider_preference_id = ?, status = 'ACTIVE'
			WHERE payment_intent_public_id = UUID_TO_BIN(?)
				AND environment = ? AND status = 'PENDING'
			""", preferenceId, paymentAttemptId.toString(), environment.name());
		if (changed != 1) {
			throw new CheckoutPaymentException("CHECKOUT_ROUTE_CONFLICT", "La ruta de pago cambió.");
		}
	}

	@Override
	public Optional<Boolean> routePreferenceMatches(
			long tenantId, UUID paymentAttemptId,
			PaymentEnvironment environment, String preferenceId) {
		return jdbcTemplate.query("""
			SELECT provider_preference_id = ?
			FROM payment_webhook_routes
			WHERE tenant_id = ? AND payment_intent_public_id = UUID_TO_BIN(?)
				AND environment = ?
			""", (resultSet, rowNumber) -> resultSet.getBoolean(1),
			preferenceId, tenantId, paymentAttemptId.toString(), environment.name())
			.stream().findFirst();
	}

	@Override
	public void expireRoute(UUID paymentAttemptId, PaymentEnvironment environment) {
		jdbcTemplate.update("""
			UPDATE payment_webhook_routes SET status = 'EXPIRED'
			WHERE payment_intent_public_id = UUID_TO_BIN(?)
				AND environment = ? AND status <> 'EXPIRED'
			""", paymentAttemptId.toString(), environment.name());
	}

	@Override
	public Optional<CheckoutRoute> findRoute(
			byte[] routeTokenHash, PaymentEnvironment environment) {
		return jdbcTemplate.query(
			ROUTE_SELECT + """
			 WHERE route.route_token_hash = ? AND route.environment = ?
				AND route.status IN ('PENDING', 'ACTIVE')
				AND tenant.status = 'ACTIVE'
			""", this::mapRoute, routeTokenHash, environment.name())
			.stream().findFirst();
	}

	@Override
	public boolean insertWebhook(CheckoutRoute route, ReceivedWebhook webhook, Instant now) {
		try {
			jdbcTemplate.update("""
				INSERT INTO payment_webhook_events (
					public_id, route_id, provider, environment, notification_id,
					request_id, event_type, action_name, provider_resource_id,
					provider_user_id, live_mode, payload_hash, status, available_at
				)
				VALUES (
					UUID_TO_BIN(?), ?, 'MERCADO_PAGO', ?, ?, ?, ?, ?, ?, ?, ?, ?,
					'RECEIVED', ?
				)
				""", UUID.randomUUID().toString(), route.internalId(),
				route.environment().name(), webhook.notificationId(), webhook.requestId(),
				webhook.eventType(), webhook.action(), webhook.providerResourceId(),
				webhook.providerUserId(), webhook.liveMode(), webhook.payloadHash(),
				Timestamp.from(now));
			return true;
		}
		catch (DuplicateKeyException exception) {
			return false;
		}
	}

	@Override
	public Optional<ClaimedWebhookEvent> claimNext(Instant now, Instant leasedUntil) {
		Optional<ClaimedWebhookEvent> candidate = jdbcTemplate.query("""
			SELECT event.id, BIN_TO_UUID(event.public_id) event_public_id,
				event.attempt_count, event.provider_resource_id,
				route.id route_id, BIN_TO_UUID(route.public_id) route_public_id,
				route.tenant_id, tenant.slug tenant_slug,
				tenant.database_key, route.environment,
				BIN_TO_UUID(route.payment_intent_public_id) payment_intent_public_id,
				route.expected_seller_account_id, route.provider_preference_id,
				route.status route_status, route.expires_at
			FROM payment_webhook_events event
			JOIN payment_webhook_routes route ON route.id = event.route_id
			JOIN tenants tenant ON tenant.id = route.tenant_id
			WHERE (
				(event.status IN ('RECEIVED', 'RETRY') AND event.available_at <= ?)
				OR (event.status = 'PROCESSING' AND event.leased_until <= ?)
			)
			ORDER BY event.id
			LIMIT 1
			FOR UPDATE SKIP LOCKED
			""", (resultSet, rowNumber) -> new ClaimedWebhookEvent(
				resultSet.getLong("id"),
				UUID.fromString(resultSet.getString("event_public_id")),
				resultSet.getInt("attempt_count") + 1,
				resultSet.getString("provider_resource_id"),
				mapRouteFromAliased(resultSet)),
			Timestamp.from(now), Timestamp.from(now)).stream().findFirst();
		if (candidate.isEmpty()) {
			return Optional.empty();
		}
		ClaimedWebhookEvent event = candidate.get();
		int changed = jdbcTemplate.update("""
			UPDATE payment_webhook_events
			SET status = 'PROCESSING', attempt_count = ?, leased_until = ?,
				last_error_code = NULL
			WHERE id = ?
			""", event.attemptCount(), Timestamp.from(leasedUntil), event.internalId());
		return changed == 1 ? Optional.of(event) : Optional.empty();
	}

	@Override
	public boolean markProcessed(long eventId, int expectedAttemptCount, Instant now) {
		return jdbcTemplate.update("""
			UPDATE payment_webhook_events
			SET status = 'PROCESSED', leased_until = NULL, processed_at = ?,
				last_error_code = NULL
			WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
			""", Timestamp.from(now), eventId, expectedAttemptCount) == 1;
	}

	@Override
	public boolean markFailed(
			long eventId, int expectedAttemptCount, boolean dead,
			String errorCode, Instant availableAt) {
		return jdbcTemplate.update("""
			UPDATE payment_webhook_events
			SET status = ?, leased_until = NULL, available_at = ?, last_error_code = ?
			WHERE id = ? AND status = 'PROCESSING' AND attempt_count = ?
			""", dead ? "DEAD" : "RETRY", Timestamp.from(availableAt), errorCode,
			eventId, expectedAttemptCount) == 1;
	}

	@Override
	public List<FailedWebhookEvent> findDeadWebhooks(
			long tenantId, PaymentEnvironment environment, int limit) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(event.public_id) event_public_id,
				event.status, event.attempt_count, event.last_error_code,
				event.updated_at
			FROM payment_webhook_events event
			JOIN payment_webhook_routes route ON route.id = event.route_id
			WHERE route.tenant_id = ? AND event.environment = ? AND event.status = 'DEAD'
			ORDER BY event.updated_at DESC, event.id DESC
			LIMIT ?
			""", (resultSet, rowNumber) -> new FailedWebhookEvent(
			UUID.fromString(resultSet.getString("event_public_id")),
			resultSet.getString("status"),
			resultSet.getInt("attempt_count"),
			resultSet.getString("last_error_code"),
			resultSet.getTimestamp("updated_at").toInstant(),
			true), tenantId, environment.name(), limit);
	}

	@Override
	public WebhookRetryOutcome retryWebhook(
			long tenantId, PaymentEnvironment environment,
			UUID eventPublicId, long actorUserId,
			UUID actorUserPublicId, Instant now) {
		Optional<WebhookState> current = jdbcTemplate.query("""
			SELECT event.status, event.attempt_count, event.available_at
			FROM payment_webhook_events event
			JOIN payment_webhook_routes route ON route.id = event.route_id
			WHERE event.public_id = UUID_TO_BIN(?) AND route.tenant_id = ?
				AND event.environment = ?
			FOR UPDATE
			""", (resultSet, rowNumber) -> new WebhookState(
			resultSet.getString("status"), resultSet.getInt("attempt_count"),
			resultSet.getTimestamp("available_at").toInstant()),
			eventPublicId.toString(), tenantId, environment.name()).stream().findFirst();
		if (current.isEmpty()) {
			return new WebhookRetryOutcome(WebhookRetryResult.NOT_FOUND, null);
		}
		WebhookState state = current.get();
		if ("PROCESSED".equals(state.status())) {
			return new WebhookRetryOutcome(WebhookRetryResult.PROCESSED, state.availableAt());
		}
		if (!"DEAD".equals(state.status())) {
			return new WebhookRetryOutcome(
				WebhookRetryResult.ALREADY_SCHEDULED, state.availableAt());
		}
		int changed = jdbcTemplate.update("""
			UPDATE payment_webhook_events
			SET status = 'RETRY', attempt_count = 0, available_at = ?,
				leased_until = NULL, processed_at = NULL,
				last_error_code = 'MANUAL_RETRY'
			WHERE public_id = UUID_TO_BIN(?) AND status = 'DEAD'
			""", Timestamp.from(now), eventPublicId.toString());
		if (changed != 1) {
			return new WebhookRetryOutcome(
				WebhookRetryResult.ALREADY_SCHEDULED, state.availableAt());
		}
		jdbcTemplate.update("""
			INSERT INTO payment_webhook_retry_audit (
				public_id, webhook_event_id, tenant_id, actor_user_id,
				actor_user_public_id, action_name, previous_attempt_count, created_at
			)
			SELECT UUID_TO_BIN(?), event.id, ?, ?, UUID_TO_BIN(?),
				'MANUAL_RETRY_SCHEDULED', ?, ?
			FROM payment_webhook_events event
			WHERE event.public_id = UUID_TO_BIN(?)
			""", UUID.randomUUID().toString(), tenantId, actorUserId,
			actorUserPublicId.toString(), state.attemptCount(), Timestamp.from(now),
			eventPublicId.toString());
		return new WebhookRetryOutcome(WebhookRetryResult.SCHEDULED, now);
	}

	private record WebhookState(String status, int attemptCount, Instant availableAt) {
	}

	private CheckoutRoute mapRoute(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CheckoutRoute(
			resultSet.getLong("id"), UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("tenant_id"), resultSet.getString("tenant_slug"),
			resultSet.getString("database_key"),
			PaymentEnvironment.valueOf(resultSet.getString("environment")),
			UUID.fromString(resultSet.getString("payment_intent_public_id")),
			resultSet.getString("expected_seller_account_id"),
			resultSet.getString("provider_preference_id"), resultSet.getString("status"),
			resultSet.getTimestamp("expires_at").toInstant());
	}

	private CheckoutRoute mapRouteFromAliased(ResultSet resultSet) throws SQLException {
		return new CheckoutRoute(
			resultSet.getLong("route_id"),
			UUID.fromString(resultSet.getString("route_public_id")),
			resultSet.getLong("tenant_id"), resultSet.getString("tenant_slug"),
			resultSet.getString("database_key"),
			PaymentEnvironment.valueOf(resultSet.getString("environment")),
			UUID.fromString(resultSet.getString("payment_intent_public_id")),
			resultSet.getString("expected_seller_account_id"),
			resultSet.getString("provider_preference_id"),
			resultSet.getString("route_status"),
			resultSet.getTimestamp("expires_at").toInstant());
	}
}
