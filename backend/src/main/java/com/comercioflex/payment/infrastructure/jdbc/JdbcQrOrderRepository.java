package com.comercioflex.payment.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.application.CheckoutOrder;
import com.comercioflex.payment.application.QrOrderException;
import com.comercioflex.payment.application.QrOrderRepository;
import com.comercioflex.payment.application.StoredQrOrderAttempt;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;

@Repository
public class JdbcQrOrderRepository implements QrOrderRepository {

	private static final String SELECT = """
		SELECT intent.id, BIN_TO_UUID(intent.public_id) public_id,
			intent.order_id, BIN_TO_UUID(order_record.public_id) order_public_id,
			order_record.id order_number, order_record.status order_status,
			order_record.reservation_expires_at,
			BIN_TO_UUID(intent.idempotency_key) idempotency_key,
			intent.request_fingerprint,
			BIN_TO_UUID(intent.transition_idempotency_key) transition_idempotency_key,
			intent.status, intent.attempt_number, intent.amount, intent.currency_code,
			intent.external_reference, intent.version,
			qr.id qr_id, BIN_TO_UUID(qr.provider_idempotency_key) provider_idempotency_key,
			qr.provider_order_id, qr.qr_data, qr.provider_status,
			qr.provider_expires_at, qr.expected_seller_account_id,
			qr.payment_environment, qr.external_pos_id, qr.creation_status,
			qr.creation_started_at, qr.version qr_version, qr.updated_at
		FROM payment_intents intent
		JOIN orders order_record ON order_record.id = intent.order_id
		JOIN merchant_qr_orders qr ON qr.payment_intent_id = intent.id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcQrOrderRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<CheckoutOrder> lockOrder(UUID orderId, byte[] lookupTokenHash) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, status, subtotal,
				currency_code, reservation_expires_at
			FROM orders
			WHERE public_id = UUID_TO_BIN(?) AND lookup_token_hash = ?
			FOR UPDATE
			""", (resultSet, rowNumber) -> new CheckoutOrder(
			resultSet.getLong("id"), UUID.fromString(resultSet.getString("public_id")),
			OrderStatus.valueOf(resultSet.getString("status")),
			resultSet.getBigDecimal("subtotal"), resultSet.getString("currency_code"),
			resultSet.getTimestamp("reservation_expires_at").toInstant()),
			orderId.toString(), lookupTokenHash).stream().findFirst();
	}

	@Override
	public Optional<StoredQrOrderAttempt> findByIdempotencyKey(UUID idempotencyKey) {
		return query(SELECT + " WHERE intent.idempotency_key = UUID_TO_BIN(?)",
			idempotencyKey.toString());
	}

	@Override
	public Optional<StoredQrOrderAttempt> findCurrentByOrder(
			UUID orderId, byte[] lookupTokenHash) {
		return query(SELECT + """
			 WHERE order_record.public_id = UUID_TO_BIN(?)
				AND order_record.lookup_token_hash = ?
				AND intent.payment_flow = 'QR_ORDER'
			 ORDER BY intent.attempt_number DESC
			 LIMIT 1
			""", orderId.toString(), lookupTokenHash);
	}

	@Override
	public Optional<StoredQrOrderAttempt> findByPublicId(
			UUID paymentAttemptId, boolean forUpdate) {
		return query(SELECT + " WHERE intent.public_id = UUID_TO_BIN(?)"
			+ (forUpdate ? " FOR UPDATE" : ""), paymentAttemptId.toString());
	}

	@Override
	public boolean hasBlockingIntent(long orderInternalId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT (
				SELECT COUNT(*) FROM payment_intents
				WHERE order_id = ?
					AND status IN ('CREATED', 'PENDING', 'APPROVED', 'REQUIRES_REVIEW')
			) + (
				SELECT COUNT(*) FROM bank_transfer_payments
				WHERE order_id = ? AND status IN ('AWAITING_RECEIPT', 'UNDER_REVIEW')
			)
			""", Integer.class, orderInternalId, orderInternalId);
		return count != null && count > 0;
	}

	@Override
	public int nextAttemptNumber(long orderInternalId) {
		Integer value = jdbcTemplate.queryForObject("""
			SELECT COALESCE(MAX(attempt_number), 0) + 1
			FROM payment_intents WHERE order_id = ?
			""", Integer.class, orderInternalId);
		return value == null ? 1 : value;
	}

	@Override
	public void insert(
			UUID paymentAttemptId, long orderInternalId, UUID idempotencyKey,
			byte[] fingerprint, UUID transitionIdempotencyKey, int attemptNumber,
			java.math.BigDecimal amount, String currencyCode, String externalReference,
			UUID providerIdempotencyKey, Instant providerExpiresAt,
			String sellerAccountId, PaymentEnvironment environment,
			String externalPosId, Instant now) {
		jdbcTemplate.update("""
			INSERT INTO payment_intents (
				public_id, order_id, idempotency_key, request_fingerprint,
				transition_idempotency_key, provider, payment_flow, status,
				attempt_number, amount, currency_code, external_reference
			)
			VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, UUID_TO_BIN(?),
				'MERCADO_PAGO', 'QR_ORDER', 'CREATED', ?, ?, ?, ?)
			""", paymentAttemptId.toString(), orderInternalId, idempotencyKey.toString(),
			fingerprint, transitionIdempotencyKey.toString(), attemptNumber, amount,
			currencyCode, externalReference);
		Long intentId = jdbcTemplate.queryForObject(
			"SELECT id FROM payment_intents WHERE public_id = UUID_TO_BIN(?)",
			Long.class, paymentAttemptId.toString());
		if (intentId == null) throw persistence();
		jdbcTemplate.update("""
			INSERT INTO merchant_qr_orders (
				public_id, payment_intent_id, provider_idempotency_key,
				provider_status, provider_expires_at, expected_seller_account_id,
				payment_environment, external_pos_id, creation_status,
				creation_started_at, created_at, updated_at
			)
			VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), 'created', ?, ?, ?, ?,
				'CREATING', ?, ?, ?)
			""", UUID.randomUUID().toString(), intentId,
			providerIdempotencyKey.toString(), Timestamp.from(providerExpiresAt),
			sellerAccountId, environment.name(), externalPosId, Timestamp.from(now),
			Timestamp.from(now), Timestamp.from(now));
	}

	@Override
	public boolean claimCreation(
			StoredQrOrderAttempt attempt, Instant now, Instant staleBefore) {
		return jdbcTemplate.update("""
			UPDATE merchant_qr_orders
			SET creation_status = 'CREATING', creation_started_at = ?,
				version = version + 1, updated_at = ?
			WHERE id = ? AND version = ?
				AND provider_order_id IS NULL
				AND (creation_status = 'FAILED' OR creation_started_at < ?)
			""", Timestamp.from(now), Timestamp.from(now), attempt.qrInternalId(),
			attempt.qrVersion(), Timestamp.from(staleBefore)) == 1;
	}

	@Override
	public void attachProviderOrder(
			StoredQrOrderAttempt attempt, String providerOrderId, String qrData,
			String providerStatus, Instant providerExpiresAt, Instant now) {
		int qrChanged = jdbcTemplate.update("""
			UPDATE merchant_qr_orders
			SET provider_order_id = ?, qr_data = ?, provider_status = ?,
				provider_expires_at = ?, creation_status = 'READY',
				version = version + 1, updated_at = ?
			WHERE id = ? AND provider_order_id IS NULL
			""", providerOrderId, qrData, providerStatus,
			Timestamp.from(providerExpiresAt), Timestamp.from(now), attempt.qrInternalId());
		if (qrChanged != 1) throw concurrent();
		int intentChanged = jdbcTemplate.update("""
			UPDATE payment_intents
			SET status = 'PENDING', version = version + 1, updated_at = ?
			WHERE id = ? AND status = 'CREATED'
			""", Timestamp.from(now), attempt.internalId());
		if (intentChanged != 1) throw concurrent();
	}

	@Override
	public void markCreationFailed(StoredQrOrderAttempt attempt, Instant now) {
		jdbcTemplate.update("""
			UPDATE merchant_qr_orders
			SET creation_status = 'FAILED', version = version + 1, updated_at = ?
			WHERE id = ? AND provider_order_id IS NULL
			""", Timestamp.from(now), attempt.qrInternalId());
	}

	@Override
	public void updateProviderStatus(
			StoredQrOrderAttempt attempt, String providerStatus, Instant now) {
		jdbcTemplate.update("""
			UPDATE merchant_qr_orders
			SET provider_status = ?, version = version + 1, updated_at = ?
			WHERE id = ?
			""", providerStatus, Timestamp.from(now), attempt.qrInternalId());
	}

	@Override
	public void updateIntentStatus(
			StoredQrOrderAttempt attempt, PaymentIntentStatus target, Instant now) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_intents
			SET status = ?, version = version + 1, updated_at = ?
			WHERE id = ? AND version = ? AND status IN ('CREATED', 'PENDING')
			""", target.name(), Timestamp.from(now), attempt.internalId(), attempt.version());
		if (changed != 1 && attempt.status() != target) throw concurrent();
	}

	private Optional<StoredQrOrderAttempt> query(String sql, Object... arguments) {
		return jdbcTemplate.query(sql, this::map, arguments).stream().findFirst();
	}

	private StoredQrOrderAttempt map(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new StoredQrOrderAttempt(
			resultSet.getLong("id"), UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("order_id"),
			UUID.fromString(resultSet.getString("order_public_id")),
			resultSet.getLong("order_number"), resultSet.getString("order_status"),
			resultSet.getTimestamp("reservation_expires_at").toInstant(),
			UUID.fromString(resultSet.getString("idempotency_key")),
			resultSet.getBytes("request_fingerprint"),
			UUID.fromString(resultSet.getString("transition_idempotency_key")),
			PaymentIntentStatus.valueOf(resultSet.getString("status")),
			resultSet.getInt("attempt_number"), resultSet.getBigDecimal("amount"),
			resultSet.getString("currency_code"), resultSet.getString("external_reference"),
			resultSet.getLong("version"), resultSet.getLong("qr_id"),
			UUID.fromString(resultSet.getString("provider_idempotency_key")),
			resultSet.getString("provider_order_id"), resultSet.getString("qr_data"),
			resultSet.getString("provider_status"),
			resultSet.getTimestamp("provider_expires_at").toInstant(),
			resultSet.getString("expected_seller_account_id"),
			PaymentEnvironment.valueOf(resultSet.getString("payment_environment")),
			resultSet.getString("external_pos_id"), resultSet.getString("creation_status"),
			resultSet.getTimestamp("creation_started_at").toInstant(),
			resultSet.getLong("qr_version"), resultSet.getTimestamp("updated_at").toInstant());
	}

	private QrOrderException persistence() {
		return new QrOrderException(
			"QR_PERSISTENCE_FAILED", "No se pudo guardar el pago QR.");
	}

	private QrOrderException concurrent() {
		return new QrOrderException(
			"QR_CONCURRENT_UPDATE", "El pago QR cambió durante la operación.", true, null);
	}
}
