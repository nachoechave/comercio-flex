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
	public Optional<OutboxEmail> claimNext(Instant eligibleAt, Instant staleSendingBefore,
			int maxAttempts) {
		Optional<ClaimCandidate> candidate = jdbc.query("""
			SELECT id, event_key, event_type, attempt_count,
				recipient, subject, html_body, text_body, status
			FROM transactional_email_outbox
			WHERE attempt_count < ? AND (
				status = 'PENDING'
				OR (status = 'FAILED' AND next_attempt_at IS NOT NULL AND next_attempt_at <= ?)
				OR (status = 'SENDING' AND sending_started_at IS NOT NULL
					AND sending_started_at <= ?)
			)
			ORDER BY CASE status
				WHEN 'PENDING' THEN 0
				WHEN 'FAILED' THEN 1
				ELSE 2
			END, created_at, id
			LIMIT 1
			FOR UPDATE SKIP LOCKED
			""", (rs, row) -> new ClaimCandidate(
			rs.getLong("id"), rs.getString("event_key"), rs.getString("event_type"),
			rs.getInt("attempt_count"), rs.getString("status"),
			new TransactionalEmail(rs.getString("recipient"), rs.getString("subject"),
				rs.getString("html_body"), rs.getString("text_body"))),
			maxAttempts, timestamp(eligibleAt), timestamp(staleSendingBefore))
			.stream().findFirst();
		if (candidate.isEmpty()) return Optional.empty();
		ClaimCandidate value = candidate.get();
		int changed = jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'SENDING', attempt_count = attempt_count + 1,
				next_attempt_at = NULL, sending_started_at = ?
			WHERE id = ? AND status = ? AND attempt_count = ?
			""", timestamp(eligibleAt), value.id(), value.status(), value.attemptCount());
		if (changed != 1) return Optional.empty();
		return Optional.of(new OutboxEmail(value.id(), value.eventKey(), value.eventType(),
			value.attemptCount() + 1, value.email()));
	}

	@Override
	public int recoverExhaustedStaleSending(Instant staleSendingBefore, int maxAttempts, int limit) {
		return jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'FAILED', next_attempt_at = NULL, sending_started_at = NULL,
				last_error = COALESCE(last_error, 'Envío interrumpido y máximo de intentos alcanzado.')
			WHERE status = 'SENDING' AND attempt_count >= ?
				AND sending_started_at IS NOT NULL AND sending_started_at <= ?
			ORDER BY created_at, id
			LIMIT ?
			""", maxAttempts, timestamp(staleSendingBefore), limit);
	}

	@Override
	public boolean markSent(long id, int attemptCount, Instant sentAt) {
		return jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'SENT', sent_at = ?, next_attempt_at = NULL,
				sending_started_at = NULL, last_error = NULL
			WHERE id = ? AND status = 'SENDING' AND attempt_count = ?
			""", timestamp(sentAt), id, attemptCount) == 1;
	}

	@Override
	public boolean markFailed(long id, int attemptCount, String error, Instant nextAttemptAt) {
		return jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'FAILED', last_error = ?, next_attempt_at = ?, sending_started_at = NULL
			WHERE id = ? AND status = 'SENDING' AND attempt_count = ?
			""", truncate(error), timestamp(nextAttemptAt), id, attemptCount) == 1;
	}

	@Override
	public boolean makeEligibleForManualRetry(long id, Instant eligibleAt) {
		return jdbc.update("""
			UPDATE transactional_email_outbox
			SET status = 'FAILED', attempt_count = 0, next_attempt_at = ?,
				sending_started_at = NULL
			WHERE id = ? AND status = 'FAILED'
			""", timestamp(eligibleAt), id) == 1;
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) return "Fallo de envío sin detalle.";
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}

	private java.sql.Timestamp timestamp(Instant value) {
		return value == null ? null : java.sql.Timestamp.from(value);
	}

	private record ClaimCandidate(long id, String eventKey, String eventType,
		int attemptCount, String status, TransactionalEmail email) { }
}
