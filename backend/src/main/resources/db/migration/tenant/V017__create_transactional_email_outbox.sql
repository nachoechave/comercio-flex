CREATE TABLE transactional_email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(180) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    order_id BIGINT NOT NULL,
    bank_transfer_payment_id BIGINT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    html_body MEDIUMTEXT NOT NULL,
    text_body MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    sent_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    CONSTRAINT pk_transactional_email_outbox PRIMARY KEY (id),
    CONSTRAINT uk_transactional_email_outbox_event UNIQUE (event_key),
    CONSTRAINT fk_transactional_email_outbox_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactional_email_outbox_bank_transfer FOREIGN KEY (bank_transfer_payment_id)
        REFERENCES bank_transfer_payments (id) ON DELETE CASCADE,
    CONSTRAINT ck_transactional_email_outbox_status CHECK (
        status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')
    ),
    CONSTRAINT ck_transactional_email_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX ix_transactional_email_outbox_delivery
    ON transactional_email_outbox (status, created_at, id);
