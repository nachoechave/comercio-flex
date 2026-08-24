package com.comercioflex.payment.infrastructure.jdbc;

import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import com.comercioflex.payment.application.CheckoutOrder;
import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.CheckoutRepository;
import com.comercioflex.payment.application.StoredCheckoutAttempt;
import com.comercioflex.payment.application.VerifiedProviderPayment;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;

@Repository
public class JdbcCheckoutRepository implements CheckoutRepository {

	private static final String ATTEMPT_SELECT = """
		SELECT intent.id, BIN_TO_UUID(intent.public_id) public_id,
			intent.order_id, BIN_TO_UUID(order_record.public_id) order_public_id,
			order_record.id order_number, order_record.status order_status,
			order_record.reservation_expires_at,
			BIN_TO_UUID(intent.idempotency_key) idempotency_key,
			intent.request_fingerprint,
			BIN_TO_UUID(intent.transition_idempotency_key) transition_idempotency_key,
			intent.status, intent.amount, intent.currency_code,
			intent.external_reference, intent.return_token_expires_at,
			intent.provider_preference_id,
			intent.checkout_url, intent.checkout_expires_at,
			intent.credential_seller_account_id, intent.payment_environment,
			intent.updated_at, intent.version
		FROM payment_intents intent
		JOIN orders order_record ON order_record.id = intent.order_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcCheckoutRepository(
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
	public Optional<StoredCheckoutAttempt> findByIdempotencyKey(UUID idempotencyKey) {
		return queryAttempt(
			ATTEMPT_SELECT + " WHERE intent.idempotency_key = UUID_TO_BIN(?)",
			idempotencyKey.toString());
	}

	@Override
	public Optional<StoredCheckoutAttempt> findByPublicId(
			UUID paymentAttemptId, boolean forUpdate) {
		return queryAttempt(
			ATTEMPT_SELECT + " WHERE intent.public_id = UUID_TO_BIN(?)"
				+ (forUpdate ? " FOR UPDATE" : ""), paymentAttemptId.toString());
	}

	@Override
	public Optional<StoredCheckoutAttempt> findByReturnTokenHash(byte[] returnTokenHash) {
		return queryAttempt(
			ATTEMPT_SELECT + " WHERE intent.return_token_hash = ?", returnTokenHash);
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
	public void insertIntent(
			UUID paymentAttemptId, long orderInternalId, UUID idempotencyKey,
			byte[] fingerprint, UUID transitionIdempotencyKey, byte[] returnTokenHash,
			Instant returnTokenExpiresAt,
			int attemptNumber, java.math.BigDecimal amount, String currencyCode) {
		jdbcTemplate.update("""
			INSERT INTO payment_intents (
				public_id, order_id, idempotency_key, request_fingerprint,
				transition_idempotency_key, provider, status, attempt_number,
				amount, currency_code, external_reference, return_token_hash,
				return_token_expires_at
			)
			VALUES (
				UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, UUID_TO_BIN(?),
				'MERCADO_PAGO', 'CREATED', ?, ?, ?, ?, ?, ?
			)
			""", paymentAttemptId.toString(), orderInternalId, idempotencyKey.toString(),
			fingerprint, transitionIdempotencyKey.toString(), attemptNumber, amount,
			currencyCode, paymentAttemptId.toString(), returnTokenHash,
			Timestamp.from(returnTokenExpiresAt));
	}

	@Override
	public void attachPreference(
			StoredCheckoutAttempt attempt, String preferenceId, URI checkoutUri,
			Instant expiresAt, String sellerAccountId, PaymentEnvironment environment,
			Instant now) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_intents
			SET provider_preference_id = ?, checkout_url = ?, checkout_expires_at = ?,
				credential_seller_account_id = ?, payment_environment = ?,
				preference_created_at = ?, status = 'PENDING', version = version + 1
			WHERE id = ? AND version = ? AND status = 'CREATED'
			""", preferenceId, checkoutUri.toString(), Timestamp.from(expiresAt),
			sellerAccountId, environment.name(), Timestamp.from(now),
			attempt.internalId(), attempt.version());
		requireChanged(changed);
	}

	@Override
	public void markCreationForReview(StoredCheckoutAttempt attempt) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_intents SET status = 'REQUIRES_REVIEW', version = version + 1
			WHERE id = ? AND version = ? AND status = 'CREATED'
			""", attempt.internalId(), attempt.version());
		requireChanged(changed);
	}

	@Override
	public void applyVerifiedPayment(
			StoredCheckoutAttempt attempt, VerifiedProviderPayment payment,
			boolean applied, boolean reviewRequired, Instant now) {
		Integer otherApplied = jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM payment_transactions
			WHERE payment_intent_id = ? AND applied_at IS NOT NULL
				AND provider_payment_id <> ?
			""", Integer.class, attempt.internalId(), payment.providerPaymentId());
		boolean effectiveApplied = applied && (otherApplied == null || otherApplied == 0);
		boolean effectiveReview = reviewRequired || (applied && !effectiveApplied);
		Optional<TransactionSnapshot> existing = jdbcTemplate.query("""
			SELECT id, payment_intent_id, provider_status, amount, currency_code, applied_at,
				review_required, version
			FROM payment_transactions
			WHERE provider = 'MERCADO_PAGO' AND provider_payment_id = ?
			FOR UPDATE
			""", this::mapTransaction, payment.providerPaymentId()).stream().findFirst();
		long transactionId;
		if (existing.isEmpty()) {
			KeyHolder keys = new GeneratedKeyHolder();
			jdbcTemplate.update(connection -> {
				PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO payment_transactions (
						public_id, payment_intent_id, provider, provider_payment_id,
						provider_status, amount, currency_code, provider_updated_at,
						applied_at, review_required
					)
					VALUES (UUID_TO_BIN(?), ?, 'MERCADO_PAGO', ?, ?, ?, ?, ?, ?, ?)
					""", java.sql.Statement.RETURN_GENERATED_KEYS);
				statement.setString(1, UUID.randomUUID().toString());
				statement.setLong(2, attempt.internalId());
				statement.setString(3, payment.providerPaymentId());
				statement.setString(4, payment.status().name());
				statement.setBigDecimal(5, payment.amount());
				statement.setString(6, payment.currencyCode());
				statement.setTimestamp(7, nullable(payment.providerUpdatedAt()));
				statement.setTimestamp(8, effectiveApplied ? Timestamp.from(now) : null);
				statement.setBoolean(9, effectiveReview);
				return statement;
			}, keys);
			Number key = keys.getKey();
			if (key == null) {
				throw new IllegalStateException("No se pudo registrar el pago verificado.");
			}
			transactionId = key.longValue();
		}
		else {
			TransactionSnapshot stored = existing.get();
			if (stored.paymentIntentInternalId() != attempt.internalId()
					|| stored.amount().compareTo(payment.amount()) != 0
					|| !stored.currencyCode().equals(payment.currencyCode())) {
				throw new CheckoutPaymentException(
					"PROVIDER_PAYMENT_CONFLICT", "El pago pertenece a otra operación.");
			}
			transactionId = stored.internalId();
			if (!stored.status().equals(payment.status().name())) {
				if (!stored.status().equals("PENDING")) {
					throw new CheckoutPaymentException(
						"PAYMENT_STATUS_REGRESSION", "El pago intentó una transición inválida.");
				}
				int changed = jdbcTemplate.update("""
					UPDATE payment_transactions
					SET provider_status = ?, provider_updated_at = ?,
						applied_at = ?, review_required = ?, version = version + 1
					WHERE id = ? AND version = ? AND provider_status = 'PENDING'
					""", payment.status().name(), nullable(payment.providerUpdatedAt()),
					effectiveApplied ? Timestamp.from(now) : null, effectiveReview,
					stored.internalId(), stored.version());
				requireChanged(changed);
			}
			else if ((effectiveApplied && stored.appliedAt() == null)
					|| (effectiveReview && !stored.reviewRequired())) {
				int changed = jdbcTemplate.update("""
					UPDATE payment_transactions
					SET applied_at = ?, review_required = ?, version = version + 1
					WHERE id = ? AND version = ?
					""", effectiveApplied ? Timestamp.from(now) : null, effectiveReview,
					stored.internalId(), stored.version());
				requireChanged(changed);
			}
		}

		if (effectiveApplied || effectiveReview) {
			String target = effectiveApplied ? "APPROVED" : "REQUIRES_REVIEW";
			int changed = jdbcTemplate.update("""
				UPDATE payment_intents SET status = ?, version = version + 1
				WHERE id = ? AND version = ? AND status = 'PENDING'
				""", target, attempt.internalId(), attempt.version());
			boolean alreadyAppliedIntent = effectiveReview
				&& attempt.status() == PaymentIntentStatus.APPROVED;
			if (changed != 1
					&& attempt.status() != PaymentIntentStatus.valueOf(target)
					&& !alreadyAppliedIntent) {
				throw new CheckoutPaymentException(
					"PAYMENT_CONCURRENT_UPDATE", "El intento de pago cambió durante el proceso.");
			}
		}
		else {
			jdbcTemplate.update(
				"UPDATE payment_intents SET updated_at = ? WHERE id = ?",
				Timestamp.from(now), attempt.internalId());
		}
	}

	@Override
	public String latestProviderStatus(long paymentIntentInternalId) {
		return jdbcTemplate.query("""
			SELECT provider_status FROM payment_transactions
			WHERE payment_intent_id = ?
			ORDER BY COALESCE(provider_updated_at, created_at) DESC, id DESC
			LIMIT 1
			""", (resultSet, rowNumber) -> resultSet.getString(1), paymentIntentInternalId)
			.stream().findFirst().orElse(null);
	}

	private Optional<StoredCheckoutAttempt> queryAttempt(String sql, Object... arguments) {
		return jdbcTemplate.query(sql, this::mapAttempt, arguments).stream().findFirst();
	}

	private StoredCheckoutAttempt mapAttempt(ResultSet resultSet, int rowNumber)
			throws SQLException {
		String checkoutUrl = resultSet.getString("checkout_url");
		String environment = resultSet.getString("payment_environment");
		return new StoredCheckoutAttempt(
			resultSet.getLong("id"), UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("order_id"),
			UUID.fromString(resultSet.getString("order_public_id")),
			resultSet.getLong("order_number"), resultSet.getString("order_status"),
			resultSet.getTimestamp("reservation_expires_at").toInstant(),
			UUID.fromString(resultSet.getString("idempotency_key")),
			resultSet.getBytes("request_fingerprint"),
			UUID.fromString(resultSet.getString("transition_idempotency_key")),
			PaymentIntentStatus.valueOf(resultSet.getString("status")),
			resultSet.getBigDecimal("amount"), resultSet.getString("currency_code"),
			resultSet.getString("external_reference"),
			resultSet.getTimestamp("return_token_expires_at").toInstant(),
			resultSet.getString("provider_preference_id"),
			checkoutUrl == null ? null : URI.create(checkoutUrl),
			toInstant(resultSet.getTimestamp("checkout_expires_at")),
			resultSet.getString("credential_seller_account_id"),
			environment == null ? null : PaymentEnvironment.valueOf(environment),
			resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("version"));
	}

	private TransactionSnapshot mapTransaction(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new TransactionSnapshot(
			resultSet.getLong("id"), resultSet.getLong("payment_intent_id"),
			resultSet.getString("provider_status"), resultSet.getBigDecimal("amount"),
			resultSet.getString("currency_code"),
			toInstant(resultSet.getTimestamp("applied_at")),
			resultSet.getBoolean("review_required"), resultSet.getLong("version"));
	}

	private Timestamp nullable(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private Instant toInstant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private void requireChanged(int changed) {
		if (changed != 1) {
			throw new CheckoutPaymentException(
				"PAYMENT_CONCURRENT_UPDATE", "El intento de pago cambió durante el proceso.");
		}
	}

	private record TransactionSnapshot(
		long internalId,
		long paymentIntentInternalId,
		String status,
		java.math.BigDecimal amount,
		String currencyCode,
		Instant appliedAt,
		boolean reviewRequired,
		long version) {
	}
}
