package com.comercioflex.payment.infrastructure.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.payment.application.QrOrderControlRepository;
import com.comercioflex.payment.application.QrOrderException;
import com.comercioflex.payment.application.QrOrderRoute;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Repository
public class JdbcQrOrderControlRepository implements QrOrderControlRepository {

	private static final String SELECT = """
		SELECT route.id, route.tenant_id, tenant.slug, tenant.database_key,
			route.environment, BIN_TO_UUID(route.payment_intent_public_id) payment_intent_public_id,
			route.provider_order_id, route.expected_seller_account_id,
			route.status, route.attempt_count, route.expires_at
		FROM merchant_qr_order_routes route
		JOIN tenants tenant ON tenant.id = route.tenant_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcQrOrderControlRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void insertRoute(
			UUID publicId, long tenantId, PaymentEnvironment environment,
			UUID paymentAttemptId, String providerOrderId,
			String expectedSellerAccountId, Instant expiresAt, Instant now) {
		jdbcTemplate.update("""
			INSERT INTO merchant_qr_order_routes (
				public_id, tenant_id, provider, environment,
				payment_intent_public_id, provider_order_id,
				expected_seller_account_id, status, available_at, expires_at,
				created_at, updated_at
			)
			VALUES (UUID_TO_BIN(?), ?, 'MERCADO_PAGO', ?, UUID_TO_BIN(?), ?, ?,
				'ACTIVE', ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE updated_at = updated_at
			""", publicId.toString(), tenantId, environment.name(),
			paymentAttemptId.toString(), providerOrderId, expectedSellerAccountId,
			Timestamp.from(now), Timestamp.from(expiresAt), Timestamp.from(now),
			Timestamp.from(now));
		QrOrderRoute stored = findByProviderOrderId(providerOrderId, environment)
			.orElseThrow(this::persistence);
		if (stored.tenantId() != tenantId
				|| !stored.paymentAttemptId().equals(paymentAttemptId)
				|| !stored.expectedSellerAccountId().equals(expectedSellerAccountId)) {
			throw new QrOrderException(
				"QR_ROUTE_CONFLICT", "La orden QR pertenece a otra operación.");
		}
	}

	@Override
	public Optional<QrOrderRoute> findByProviderOrderId(
			String providerOrderId, PaymentEnvironment environment) {
		return jdbcTemplate.query(SELECT + """
			 WHERE route.provider = 'MERCADO_PAGO'
				AND route.environment = ? AND route.provider_order_id = ?
			""", this::map, environment.name(), providerOrderId)
			.stream().findFirst();
	}

	@Override
	public Optional<QrOrderRoute> claimNext(Instant now, Instant leasedUntil) {
		Optional<QrOrderRoute> found = jdbcTemplate.query(SELECT + """
			 WHERE route.status = 'ACTIVE'
				AND route.available_at <= ?
				AND (route.leased_until IS NULL OR route.leased_until < ?)
			 ORDER BY route.available_at, route.id
			 LIMIT 1
			 FOR UPDATE SKIP LOCKED
			""", this::map, Timestamp.from(now), Timestamp.from(now))
			.stream().findFirst();
		if (found.isEmpty()) return Optional.empty();
		QrOrderRoute route = found.get();
		int changed = jdbcTemplate.update("""
			UPDATE merchant_qr_order_routes
			SET leased_until = ?, attempt_count = attempt_count + 1,
				last_error_code = NULL, updated_at = ?
			WHERE id = ? AND status = 'ACTIVE'
			""", Timestamp.from(leasedUntil), Timestamp.from(now), route.internalId());
		if (changed != 1) return Optional.empty();
		return Optional.of(new QrOrderRoute(
			route.internalId(), route.tenantId(), route.tenantSlug(),
			route.tenantDatabaseKey(), route.environment(), route.paymentAttemptId(),
			route.providerOrderId(), route.expectedSellerAccountId(), route.status(),
			route.attemptCount() + 1, route.expiresAt()));
	}

	@Override
	public void release(
			long routeId, int attemptCount, String safeErrorCode, Instant availableAt) {
		jdbcTemplate.update("""
			UPDATE merchant_qr_order_routes
			SET leased_until = NULL, last_error_code = ?, available_at = ?, updated_at = ?
			WHERE id = ? AND status = 'ACTIVE' AND attempt_count = ?
			""", safeErrorCode, Timestamp.from(availableAt), Timestamp.from(Instant.now()),
			routeId, attemptCount);
	}

	@Override
	public void complete(long routeId, String status, Instant now) {
		if (!status.equals("COMPLETED") && !status.equals("EXPIRED")) {
			throw new IllegalArgumentException("Estado de ruta QR inválido.");
		}
		jdbcTemplate.update("""
			UPDATE merchant_qr_order_routes
			SET status = ?, leased_until = NULL, last_error_code = NULL, updated_at = ?
			WHERE id = ? AND status = 'ACTIVE'
			""", status, Timestamp.from(now), routeId);
	}

	private QrOrderRoute map(ResultSet resultSet, int rowNumber) throws SQLException {
		return new QrOrderRoute(
			resultSet.getLong("id"), resultSet.getLong("tenant_id"),
			resultSet.getString("slug"), resultSet.getString("database_key"),
			PaymentEnvironment.valueOf(resultSet.getString("environment")),
			UUID.fromString(resultSet.getString("payment_intent_public_id")),
			resultSet.getString("provider_order_id"),
			resultSet.getString("expected_seller_account_id"),
			resultSet.getString("status"), resultSet.getInt("attempt_count"),
			resultSet.getTimestamp("expires_at").toInstant());
	}

	private QrOrderException persistence() {
		return new QrOrderException(
			"QR_ROUTE_PERSISTENCE_FAILED", "No se pudo enrutar la orden QR.");
	}
}
