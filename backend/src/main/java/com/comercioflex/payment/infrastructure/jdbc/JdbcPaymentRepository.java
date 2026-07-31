package com.comercioflex.payment.infrastructure.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.application.GatewayPayment;
import com.comercioflex.payment.application.LockedPaymentOrder;
import com.comercioflex.payment.application.PaymentConflictException;
import com.comercioflex.payment.application.PaymentRepository;
import com.comercioflex.payment.application.StoredPaymentIntent;
import com.comercioflex.payment.application.StoredPaymentTransaction;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentProvider;
import com.comercioflex.payment.domain.PaymentResultStatus;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

	private static final String INTENT_SELECT = """
		SELECT intent.id,
			BIN_TO_UUID(intent.public_id) public_id,
			intent.order_id,
			BIN_TO_UUID(order_record.public_id) order_public_id,
			BIN_TO_UUID(intent.idempotency_key) idempotency_key,
			intent.request_fingerprint,
			BIN_TO_UUID(intent.transition_idempotency_key) transition_idempotency_key,
			intent.provider,
			intent.status,
			intent.attempt_number,
			intent.amount,
			intent.currency_code,
			intent.external_reference,
			intent.version
		FROM payment_intents intent
		JOIN orders order_record ON order_record.id = intent.order_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcPaymentRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<LockedPaymentOrder> lockOrder(UUID orderId) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, status, subtotal,
				currency_code, reservation_expires_at
			FROM orders
			WHERE public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new LockedPaymentOrder(
				resultSet.getLong("id"),
				UUID.fromString(resultSet.getString("public_id")),
				OrderStatus.valueOf(resultSet.getString("status")),
				resultSet.getBigDecimal("subtotal"),
				resultSet.getString("currency_code"),
				resultSet.getTimestamp("reservation_expires_at").toInstant()),
			orderId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<StoredPaymentIntent> findByIdempotencyKey(UUID idempotencyKey) {
		return queryIntent(
			INTENT_SELECT + " WHERE intent.idempotency_key = UUID_TO_BIN(?)",
			idempotencyKey.toString());
	}

	@Override
	public Optional<StoredPaymentIntent> findByPublicId(UUID paymentIntentId) {
		return queryIntent(
			INTENT_SELECT + " WHERE intent.public_id = UUID_TO_BIN(?)",
			paymentIntentId.toString());
	}

	@Override
	public Optional<StoredPaymentIntent> lockIntent(UUID paymentIntentId) {
		return queryIntent(
			INTENT_SELECT + " WHERE intent.public_id = UUID_TO_BIN(?) FOR UPDATE",
			paymentIntentId.toString());
	}

	@Override
	public boolean hasBlockingIntent(long orderInternalId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM payment_intents
			WHERE order_id = ?
				AND status IN ('CREATED', 'PENDING', 'APPROVED', 'REQUIRES_REVIEW')
			""",
			Integer.class,
			orderInternalId);
		return count != null && count > 0;
	}

	@Override
	public int nextAttemptNumber(long orderInternalId) {
		Integer attempt = jdbcTemplate.queryForObject("""
			SELECT COALESCE(MAX(attempt_number), 0) + 1
			FROM payment_intents
			WHERE order_id = ?
			""",
			Integer.class,
			orderInternalId);
		return attempt == null ? 1 : attempt;
	}

	@Override
	public long insertIntent(
			UUID paymentIntentId,
			long orderInternalId,
			UUID idempotencyKey,
			byte[] requestFingerprint,
			UUID transitionIdempotencyKey,
			PaymentProvider provider,
			int attemptNumber,
			java.math.BigDecimal amount,
			String currencyCode,
			String externalReference) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO payment_intents (
					public_id, order_id, idempotency_key, request_fingerprint,
					transition_idempotency_key, provider, status, attempt_number,
					amount, currency_code, external_reference
				)
				VALUES (
					UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?,
					'CREATED', ?, ?, ?, ?
				)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, paymentIntentId.toString());
			statement.setLong(2, orderInternalId);
			statement.setString(3, idempotencyKey.toString());
			statement.setBytes(4, requestFingerprint);
			statement.setString(5, transitionIdempotencyKey.toString());
			statement.setString(6, provider.name());
			statement.setInt(7, attemptNumber);
			statement.setBigDecimal(8, amount);
			statement.setString(9, currencyCode);
			statement.setString(10, externalReference);
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("No se pudo crear el intento de pago.");
		}
		return key.longValue();
	}

	@Override
	public Optional<StoredPaymentTransaction> findTransaction(
			PaymentProvider provider,
			String providerPaymentId) {
		return jdbcTemplate.query("""
			SELECT id, payment_intent_id, provider, provider_payment_id,
				provider_status, amount, currency_code,
				applied_at IS NOT NULL applied, review_required
			FROM payment_transactions
			WHERE provider = ? AND provider_payment_id = ?
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new StoredPaymentTransaction(
				resultSet.getLong("id"),
				resultSet.getLong("payment_intent_id"),
				PaymentProvider.valueOf(resultSet.getString("provider")),
				resultSet.getString("provider_payment_id"),
				PaymentResultStatus.valueOf(resultSet.getString("provider_status")),
				resultSet.getBigDecimal("amount"),
				resultSet.getString("currency_code"),
				resultSet.getBoolean("applied"),
				resultSet.getBoolean("review_required")),
			provider.name(),
			providerPaymentId)
			.stream()
			.findFirst();
	}

	@Override
	public long insertTransaction(
			UUID transactionId,
			long paymentIntentInternalId,
			PaymentProvider provider,
			GatewayPayment payment) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO payment_transactions (
					public_id, payment_intent_id, provider, provider_payment_id,
					provider_status, amount, currency_code
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, transactionId.toString());
			statement.setLong(2, paymentIntentInternalId);
			statement.setString(3, provider.name());
			statement.setString(4, payment.providerPaymentId());
			statement.setString(5, payment.status().name());
			statement.setBigDecimal(6, payment.amount());
			statement.setString(7, payment.currencyCode());
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("No se pudo registrar el pago.");
		}
		return key.longValue();
	}

	@Override
	public void updateIntentStatus(
			long paymentIntentInternalId,
			long version,
			PaymentIntentStatus expectedStatus,
			PaymentIntentStatus status) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_intents
			SET status = ?, version = version + 1
			WHERE id = ? AND version = ? AND status = ?
			""",
			status.name(),
			paymentIntentInternalId,
			version,
			expectedStatus.name());
		if (changed != 1) {
			throw new PaymentConflictException(
				"El intento de pago cambió durante la operación.");
		}
	}

	@Override
	public void markTransactionApplied(long transactionInternalId, Instant appliedAt) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_transactions
			SET applied_at = ?
			WHERE id = ? AND applied_at IS NULL AND review_required = FALSE
			""",
			Timestamp.from(appliedAt),
			transactionInternalId);
		if (changed != 1) {
			throw new PaymentConflictException("El pago ya fue procesado.");
		}
	}

	@Override
	public void markTransactionForReview(long transactionInternalId) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_transactions
			SET review_required = TRUE
			WHERE id = ? AND applied_at IS NULL AND review_required = FALSE
			""",
			transactionInternalId);
		if (changed != 1) {
			throw new PaymentConflictException("El pago ya fue procesado.");
		}
	}

	private Optional<StoredPaymentIntent> queryIntent(String sql, Object... arguments) {
		return jdbcTemplate.query(sql, this::mapIntent, arguments)
			.stream()
			.findFirst();
	}

	private StoredPaymentIntent mapIntent(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new StoredPaymentIntent(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("order_id"),
			UUID.fromString(resultSet.getString("order_public_id")),
			UUID.fromString(resultSet.getString("idempotency_key")),
			resultSet.getBytes("request_fingerprint"),
			UUID.fromString(resultSet.getString("transition_idempotency_key")),
			PaymentProvider.valueOf(resultSet.getString("provider")),
			PaymentIntentStatus.valueOf(resultSet.getString("status")),
			resultSet.getInt("attempt_number"),
			resultSet.getBigDecimal("amount"),
			resultSet.getString("currency_code"),
			resultSet.getString("external_reference"),
			resultSet.getLong("version"));
	}
}
