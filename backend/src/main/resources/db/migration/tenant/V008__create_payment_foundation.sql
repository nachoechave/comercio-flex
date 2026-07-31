CREATE TABLE payment_intents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    order_id BIGINT NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    transition_idempotency_key BINARY(16) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    blocking_order_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('CREATED', 'PENDING', 'APPROVED', 'REQUIRES_REVIEW')
            THEN order_id
            ELSE NULL
        END
    ) STORED,
    attempt_number INT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    external_reference VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_intents PRIMARY KEY (id),
    CONSTRAINT uk_payment_intents_public_id UNIQUE (public_id),
    CONSTRAINT uk_payment_intents_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_payment_intents_transition UNIQUE (transition_idempotency_key),
    CONSTRAINT uk_payment_intents_external_reference UNIQUE (external_reference),
    CONSTRAINT uk_payment_intents_order_attempt UNIQUE (order_id, attempt_number),
    CONSTRAINT uk_payment_intents_one_blocking UNIQUE (blocking_order_id),
    CONSTRAINT fk_payment_intents_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_intents_provider
        CHECK (provider IN ('FAKE', 'MERCADO_PAGO')),
    CONSTRAINT ck_payment_intents_status CHECK (
        status IN (
            'CREATED',
            'PENDING',
            'APPROVED',
            'REJECTED',
            'REQUIRES_REVIEW'
        )
    ),
    CONSTRAINT ck_payment_intents_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_payment_intents_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_intents_currency
        CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_intents_external_reference
        CHECK (CHAR_LENGTH(TRIM(external_reference)) > 0),
    CONSTRAINT ck_payment_intents_version CHECK (version >= 0)
);

CREATE INDEX ix_payment_intents_order_status
    ON payment_intents (order_id, status, created_at DESC, id DESC);

CREATE TABLE payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    payment_intent_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(100) NOT NULL,
    provider_status VARCHAR(40) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    applied_at TIMESTAMP(6) NULL,
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_public_id UNIQUE (public_id),
    CONSTRAINT uk_payment_transactions_provider_payment
        UNIQUE (provider, provider_payment_id),
    CONSTRAINT fk_payment_transactions_intent FOREIGN KEY (payment_intent_id)
        REFERENCES payment_intents (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_transactions_provider
        CHECK (provider IN ('FAKE', 'MERCADO_PAGO')),
    CONSTRAINT ck_payment_transactions_status
        CHECK (provider_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_payment_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_transactions_currency
        CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_transactions_provider_payment_id
        CHECK (CHAR_LENGTH(TRIM(provider_payment_id)) > 0),
    CONSTRAINT ck_payment_transactions_review_boolean
        CHECK (review_required IN (FALSE, TRUE)),
    CONSTRAINT ck_payment_transactions_application CHECK (
        NOT (applied_at IS NOT NULL AND review_required = TRUE)
        AND (
            provider_status = 'APPROVED'
            OR (applied_at IS NULL AND review_required = FALSE)
        )
    )
);

CREATE INDEX ix_payment_transactions_intent_created
    ON payment_transactions (payment_intent_id, created_at DESC, id DESC);
CREATE INDEX ix_payment_intents_review
    ON payment_intents (status, updated_at, id);
CREATE INDEX ix_payment_transactions_review
    ON payment_transactions (review_required, created_at, id);
