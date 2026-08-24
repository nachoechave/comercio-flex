ALTER TABLE store_settings
    ADD COLUMN bank_transfer_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER pickup_instructions,
    ADD COLUMN bank_name VARCHAR(120) NULL AFTER bank_transfer_enabled,
    ADD COLUMN bank_account_holder VARCHAR(160) NULL AFTER bank_name,
    ADD COLUMN bank_alias VARCHAR(120) NULL AFTER bank_account_holder,
    ADD COLUMN bank_cbu_cvu VARCHAR(40) NULL AFTER bank_alias,
    ADD CONSTRAINT ck_store_settings_bank_transfer CHECK (
        bank_transfer_enabled = FALSE
        OR (
            CHAR_LENGTH(TRIM(bank_account_holder)) > 0
            AND (
                CHAR_LENGTH(TRIM(bank_alias)) > 0
                OR CHAR_LENGTH(TRIM(bank_cbu_cvu)) > 0
            )
        )
    );

CREATE TABLE bank_transfer_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    order_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    receipt_object_key VARCHAR(500) NULL,
    receipt_original_filename VARCHAR(255) NULL,
    receipt_content_type VARCHAR(100) NULL,
    receipt_size BIGINT NULL,
    receipt_uploaded_at TIMESTAMP(6) NULL,
    reviewed_at TIMESTAMP(6) NULL,
    reviewed_by BIGINT NULL,
    rejection_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_bank_transfer_payments PRIMARY KEY (id),
    CONSTRAINT uk_bank_transfer_payments_public_id UNIQUE (public_id),
    CONSTRAINT uk_bank_transfer_payments_order_attempt UNIQUE (order_id, attempt_number),
    CONSTRAINT fk_bank_transfer_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_bank_transfer_payments_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_bank_transfer_payments_status CHECK (
        status IN ('AWAITING_RECEIPT', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_bank_transfer_payments_receipt_size CHECK (
        receipt_size IS NULL OR (receipt_size > 0 AND receipt_size <= 5242880)
    ),
    CONSTRAINT ck_bank_transfer_payments_version CHECK (version >= 0),
    CONSTRAINT ck_bank_transfer_payments_receipt CHECK (
        status = 'AWAITING_RECEIPT'
        OR (
            receipt_object_key IS NOT NULL
            AND receipt_original_filename IS NOT NULL
            AND receipt_content_type IS NOT NULL
            AND receipt_size IS NOT NULL
            AND receipt_uploaded_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_bank_transfer_payments_review CHECK (
        status IN ('AWAITING_RECEIPT', 'UNDER_REVIEW')
        OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    ),
    CONSTRAINT ck_bank_transfer_payments_rejection CHECK (
        status <> 'REJECTED' OR CHAR_LENGTH(TRIM(rejection_reason)) > 0
    )
);

CREATE INDEX ix_bank_transfer_payments_status_created
    ON bank_transfer_payments (status, created_at, id);
CREATE INDEX ix_bank_transfer_payments_order_created
    ON bank_transfer_payments (order_id, created_at DESC, id DESC);
