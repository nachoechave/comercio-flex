package com.comercioflex.payment.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.application.BankTransferConfiguration;
import com.comercioflex.payment.application.BankTransferOrder;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.payment.application.BankTransferPaymentException;
import com.comercioflex.payment.application.BankTransferRepository;
import com.comercioflex.payment.domain.BankTransferStatus;

@Repository
public class JdbcBankTransferRepository implements BankTransferRepository {

	private static final String PAYMENT_SELECT = """
		SELECT payment.id, BIN_TO_UUID(payment.public_id) public_id,
			payment.order_id, BIN_TO_UUID(order_record.public_id) order_public_id,
			order_record.id order_number, order_record.customer_name,
			order_record.subtotal, order_record.currency_code,
			order_record.reservation_expires_at, payment.attempt_number,
			payment.status, payment.receipt_object_key,
			payment.receipt_original_filename, payment.receipt_content_type,
			payment.receipt_size, payment.receipt_uploaded_at,
			payment.reviewed_at, payment.reviewed_by, payment.rejection_reason,
			payment.created_at, payment.updated_at, payment.version
		FROM bank_transfer_payments payment
		JOIN orders order_record ON order_record.id = payment.order_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcBankTransferRepository(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public BankTransferConfiguration findConfiguration() {
		return jdbcTemplate.query("""
			SELECT bank_transfer_enabled, bank_name, bank_account_holder,
				bank_alias, bank_cbu_cvu
			FROM store_settings ORDER BY id LIMIT 1
			""", (resultSet, rowNumber) -> new BankTransferConfiguration(
			resultSet.getBoolean("bank_transfer_enabled"),
			resultSet.getString("bank_name"),
			resultSet.getString("bank_account_holder"),
			resultSet.getString("bank_alias"),
			resultSet.getString("bank_cbu_cvu"))).stream().findFirst()
			.orElse(new BankTransferConfiguration(false, null, null, null, null));
	}

	@Override
	public Optional<BankTransferOrder> lockOrder(UUID orderId, byte[] lookupTokenHash) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, status, customer_name,
				subtotal, currency_code, reservation_expires_at
			FROM orders
			WHERE public_id = UUID_TO_BIN(?) AND lookup_token_hash = ?
			FOR UPDATE
			""", (resultSet, rowNumber) -> new BankTransferOrder(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("id"),
			OrderStatus.valueOf(resultSet.getString("status")),
			resultSet.getString("customer_name"),
			resultSet.getBigDecimal("subtotal"),
			resultSet.getString("currency_code"),
			resultSet.getTimestamp("reservation_expires_at").toInstant()),
			orderId.toString(), lookupTokenHash).stream().findFirst();
	}

	@Override
	public boolean hasBlockingCheckout(long orderInternalId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM payment_intents
			WHERE order_id = ? AND status IN ('CREATED', 'PENDING', 'APPROVED', 'REQUIRES_REVIEW')
			""", Integer.class, orderInternalId);
		return count != null && count > 0;
	}

	@Override
	public Optional<BankTransferPayment> findCurrentForOrder(long orderInternalId) {
		return queryPayment(PAYMENT_SELECT + """
			WHERE payment.order_id = ?
			ORDER BY payment.attempt_number DESC LIMIT 1
			""", orderInternalId);
	}

	@Override
	public int nextAttemptNumber(long orderInternalId) {
		Integer value = jdbcTemplate.queryForObject("""
			SELECT COALESCE(MAX(attempt_number), 0) + 1
			FROM bank_transfer_payments WHERE order_id = ?
			""", Integer.class, orderInternalId);
		return value == null ? 1 : value;
	}

	@Override
	public void extendReservation(long orderInternalId, Instant expiresAt) {
		Timestamp timestamp = Timestamp.from(expiresAt);
		int orders = jdbcTemplate.update("""
			UPDATE orders SET reservation_expires_at = ?
			WHERE id = ? AND status = 'PENDING_CONFIRMATION'
			""", timestamp, orderInternalId);
		int reservations = jdbcTemplate.update("""
			UPDATE inventory_reservations SET expires_at = ?
			WHERE order_id = ? AND status = 'ACTIVE'
			""", timestamp, orderInternalId);
		if (orders != 1 || reservations < 1) {
			throw conflict("BANK_TRANSFER_RESERVATION_UNAVAILABLE",
				"La reserva del pedido ya no está disponible.");
		}
	}

	@Override
	public void insert(UUID paymentId, long orderInternalId, int attemptNumber) {
		jdbcTemplate.update("""
			INSERT INTO bank_transfer_payments (
				public_id, order_id, attempt_number, status
			) VALUES (UUID_TO_BIN(?), ?, ?, 'AWAITING_RECEIPT')
			""", paymentId.toString(), orderInternalId, attemptNumber);
	}

	@Override
	public Optional<BankTransferPayment> findById(UUID paymentId, boolean forUpdate) {
		return queryPayment(PAYMENT_SELECT
			+ " WHERE payment.public_id = UUID_TO_BIN(?)"
			+ (forUpdate ? " FOR UPDATE" : ""), paymentId.toString());
	}

	@Override
	public Optional<BankTransferPayment> findByIdAndOrderToken(
			UUID paymentId, UUID orderId, byte[] lookupTokenHash, boolean forUpdate) {
		return queryPayment(PAYMENT_SELECT + """
			WHERE payment.public_id = UUID_TO_BIN(?)
				AND order_record.public_id = UUID_TO_BIN(?)
				AND order_record.lookup_token_hash = ?
			""" + (forUpdate ? " FOR UPDATE" : ""),
			paymentId.toString(), orderId.toString(), lookupTokenHash);
	}

	@Override
	public void attachReceipt(
			BankTransferPayment payment, String objectKey, String originalFilename,
			String contentType, long size, Instant uploadedAt) {
		int changed = jdbcTemplate.update("""
			UPDATE bank_transfer_payments
			SET status = 'UNDER_REVIEW', receipt_object_key = ?,
				receipt_original_filename = ?, receipt_content_type = ?,
				receipt_size = ?, receipt_uploaded_at = ?, version = version + 1
			WHERE id = ? AND version = ? AND status = 'AWAITING_RECEIPT'
			""", objectKey, originalFilename, contentType, size,
			Timestamp.from(uploadedAt), payment.internalId(), payment.version());
		requireChanged(changed);
	}

	@Override
	public List<BankTransferPayment> findPendingReview(int limit) {
		return jdbcTemplate.query(PAYMENT_SELECT + """
			WHERE payment.status = 'UNDER_REVIEW'
			ORDER BY payment.receipt_uploaded_at, payment.id
			LIMIT ?
			""", this::mapPayment, limit);
	}

	@Override
	public void approve(BankTransferPayment payment, long reviewerId, Instant reviewedAt) {
		int changed = jdbcTemplate.update("""
			UPDATE bank_transfer_payments
			SET status = 'APPROVED', reviewed_at = ?, reviewed_by = ?,
				rejection_reason = NULL, version = version + 1
			WHERE id = ? AND version = ? AND status = 'UNDER_REVIEW'
			""", Timestamp.from(reviewedAt), reviewerId,
			payment.internalId(), payment.version());
		requireChanged(changed);
	}

	@Override
	public void reject(
			BankTransferPayment payment, long reviewerId, String reason, Instant reviewedAt) {
		int changed = jdbcTemplate.update("""
			UPDATE bank_transfer_payments
			SET status = 'REJECTED', reviewed_at = ?, reviewed_by = ?,
				rejection_reason = ?, version = version + 1
			WHERE id = ? AND version = ? AND status = 'UNDER_REVIEW'
			""", Timestamp.from(reviewedAt), reviewerId, reason,
			payment.internalId(), payment.version());
		requireChanged(changed);
	}

	private Optional<BankTransferPayment> queryPayment(String sql, Object... arguments) {
		return jdbcTemplate.query(sql, this::mapPayment, arguments).stream().findFirst();
	}

	private BankTransferPayment mapPayment(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new BankTransferPayment(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("order_id"),
			UUID.fromString(resultSet.getString("order_public_id")),
			resultSet.getLong("order_number"),
			resultSet.getString("customer_name"),
			resultSet.getBigDecimal("subtotal"),
			resultSet.getString("currency_code"),
			resultSet.getTimestamp("reservation_expires_at").toInstant(),
			resultSet.getInt("attempt_number"),
			BankTransferStatus.valueOf(resultSet.getString("status")),
			resultSet.getString("receipt_object_key"),
			resultSet.getString("receipt_original_filename"),
			resultSet.getString("receipt_content_type"),
			nullableLong(resultSet, "receipt_size"),
			toInstant(resultSet.getTimestamp("receipt_uploaded_at")),
			toInstant(resultSet.getTimestamp("reviewed_at")),
			nullableLong(resultSet, "reviewed_by"),
			resultSet.getString("rejection_reason"),
			resultSet.getTimestamp("created_at").toInstant(),
			resultSet.getTimestamp("updated_at").toInstant(),
			resultSet.getLong("version"));
	}

	private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private Instant toInstant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private void requireChanged(int changed) {
		if (changed != 1) {
			throw conflict("BANK_TRANSFER_CONCURRENT_UPDATE",
				"La transferencia cambió durante la operación.");
		}
	}

	private BankTransferPaymentException conflict(String code, String message) {
		return new BankTransferPaymentException(code, message);
	}
}
