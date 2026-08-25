ALTER TABLE transactional_email_outbox
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL AFTER attempt_count,
    ADD COLUMN sending_started_at TIMESTAMP(6) NULL AFTER next_attempt_at;

UPDATE transactional_email_outbox
SET next_attempt_at = updated_at
WHERE status = 'FAILED';

UPDATE transactional_email_outbox
SET sending_started_at = updated_at
WHERE status = 'SENDING';

DROP INDEX ix_transactional_email_outbox_delivery ON transactional_email_outbox;

CREATE INDEX ix_transactional_email_outbox_delivery
    ON transactional_email_outbox (
        status, next_attempt_at, sending_started_at, created_at, id
    );
