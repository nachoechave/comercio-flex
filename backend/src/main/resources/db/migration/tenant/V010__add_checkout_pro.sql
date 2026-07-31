ALTER TABLE payment_intents
    ADD COLUMN return_token_hash BINARY(32) NULL AFTER external_reference,
    ADD COLUMN return_token_expires_at TIMESTAMP(6) NULL AFTER return_token_hash,
    ADD COLUMN provider_preference_id VARCHAR(100) NULL AFTER return_token_expires_at,
    ADD COLUMN checkout_url VARCHAR(2048) NULL AFTER provider_preference_id,
    ADD COLUMN checkout_expires_at TIMESTAMP(6) NULL AFTER checkout_url,
    ADD COLUMN credential_seller_account_id VARCHAR(100) NULL
        AFTER checkout_expires_at,
    ADD COLUMN payment_environment VARCHAR(20) NULL
        AFTER credential_seller_account_id,
    ADD COLUMN preference_created_at TIMESTAMP(6) NULL
        AFTER payment_environment,
    ADD CONSTRAINT uk_payment_intents_return_token UNIQUE (return_token_hash),
    ADD CONSTRAINT uk_payment_intents_preference UNIQUE (
        provider, provider_preference_id
    ),
    ADD CONSTRAINT ck_payment_intents_checkout CHECK (
        provider = 'FAKE'
        OR (
            provider = 'MERCADO_PAGO'
            AND
            status IN ('CREATED', 'REQUIRES_REVIEW')
            AND return_token_hash IS NOT NULL
            AND return_token_expires_at IS NOT NULL
            AND provider_preference_id IS NULL
            AND checkout_url IS NULL
            AND preference_created_at IS NULL
        )
        OR
        (
            provider = 'MERCADO_PAGO'
            AND
            status <> 'CREATED'
            AND return_token_hash IS NOT NULL
            AND return_token_expires_at IS NOT NULL
            AND provider_preference_id IS NOT NULL
            AND checkout_url IS NOT NULL
            AND checkout_expires_at IS NOT NULL
            AND credential_seller_account_id IS NOT NULL
            AND payment_environment IN ('TEST', 'PRODUCTION')
            AND preference_created_at IS NOT NULL
        )
    );

ALTER TABLE payment_transactions
    ADD COLUMN provider_updated_at TIMESTAMP(6) NULL AFTER currency_code,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER review_required,
    ADD CONSTRAINT ck_payment_transactions_version CHECK (version >= 0);

CREATE INDEX ix_payment_intents_return_status
    ON payment_intents (return_token_hash, return_token_expires_at, status, updated_at);
