package com.comercioflex.notification.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.OutboxEmail;
import com.comercioflex.notification.application.TransactionalEmail;

@Repository
class JdbcNotificationOutboxRepository implements NotificationOutboxRepository {
	private final JdbcTemplate jdbc;

	JdbcNotificationOutboxRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean enqueue(String eventKey, String eventType, UUID orderId,
			Long bankTransferInternalId, TransactionalEmail email) {
		return jdbc.update("""
			INSERT IGNORE INTO transactional_email_outbox (
				event_key, event_type, order_id, bank_transfer_payment_id,
				recipient, subject, html_body, text_body
			)
			SELECT ?, ?, id, ?, ?, ?, ?, ? FROM orders WHERE public_id = UUID_TO_BIN(?)
			""", eventKey, eventType, bankTransferInternalId,
			email.recipient(), email.subject(), email.htmlBody(), email.textBody(), orderId.toString()) == 1;
	}

	@Override
	public Optional<OutboxEmail> claim(String eventKey) {
		int changed = jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'SENDING', attempt_count = attempt_count + 1, last_error = NULL
			WHERE event_key = ? AND status IN ('PENDING', 'FAILED')
			""", eventKey);
		if (changed != 1) return Optional.empty();
		return jdbc.query("""
			SELECT id, event_key, recipient, subject, html_body, text_body
			FROM transactional_email_outbox WHERE event_key = ?
			""", (rs, row) -> new OutboxEmail(rs.getLong("id"), rs.getString("event_key"),
			new TransactionalEmail(rs.getString("recipient"), rs.getString("subject"),
				rs.getString("html_body"), rs.getString("text_body"))), eventKey)
			.stream().findFirst();
	}

	@Override
	public void markSent(long id, Instant sentAt) {
		jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'SENT', sent_at = ?, last_error = NULL WHERE id = ? AND status = 'SENDING'
			""", java.sql.Timestamp.from(sentAt), id);
	}

	@Override
	public void markFailed(long id, String error) {
		jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'FAILED', last_error = ? WHERE id = ? AND status = 'SENDING'
			""", truncate(error), id);
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) return "Fallo de envío sin detalle.";
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}
}
