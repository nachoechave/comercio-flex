ALTER TABLE payment_intents
    ADD COLUMN payment_flow VARCHAR(30) NOT NULL DEFAULT 'CHECKOUT_PRO'
        AFTER provider,
    ADD CONSTRAINT ck_payment_intents_flow
        CHECK (payment_flow IN ('CHECKOUT_PRO', 'QR_ORDER'));

ALTER TABLE payment_intents
    DROP CHECK ck_payment_intents_checkout,
    ADD CONSTRAINT ck_payment_intents_checkout CHECK (
        provider = 'FAKE'
        OR (
            provider = 'MERCADO_PAGO'
            AND payment_flow = 'CHECKOUT_PRO'
            AND (
                (
                    status IN ('CREATED', 'REQUIRES_REVIEW')
                    AND return_token_hash IS NOT NULL
                    AND return_token_expires_at IS NOT NULL
                    AND provider_preference_id IS NULL
                    AND checkout_url IS NULL
                    AND preference_created_at IS NULL
                )
                OR (
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
            )
        )
        OR (
            provider = 'MERCADO_PAGO'
            AND payment_flow = 'QR_ORDER'
            AND return_token_hash IS NULL
            AND return_token_expires_at IS NULL
            AND provider_preference_id IS NULL
            AND checkout_url IS NULL
            AND checkout_expires_at IS NULL
            AND credential_seller_account_id IS NULL
            AND payment_environment IS NULL
            AND preference_created_at IS NULL
        )
    );

CREATE TABLE merchant_qr_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    payment_intent_id BIGINT NOT NULL,
    provider_idempotency_key BINARY(16) NOT NULL,
    provider_order_id VARCHAR(100) NULL,
    qr_data TEXT NULL,
    provider_status VARCHAR(40) NOT NULL,
    provider_expires_at TIMESTAMP(6) NOT NULL,
    expected_seller_account_id VARCHAR(100) NOT NULL,
    payment_environment VARCHAR(20) NOT NULL,
    external_pos_id VARCHAR(40) NOT NULL,
    creation_status VARCHAR(20) NOT NULL,
    creation_started_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merchant_qr_orders PRIMARY KEY (id),
    CONSTRAINT uk_merchant_qr_orders_public_id UNIQUE (public_id),
    CONSTRAINT uk_merchant_qr_orders_intent UNIQUE (payment_intent_id),
    CONSTRAINT uk_merchant_qr_orders_idempotency UNIQUE (provider_idempotency_key),
    CONSTRAINT uk_merchant_qr_orders_provider_order UNIQUE (provider_order_id),
    CONSTRAINT fk_merchant_qr_orders_intent FOREIGN KEY (payment_intent_id)
        REFERENCES payment_intents (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchant_qr_orders_environment
        CHECK (payment_environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_merchant_qr_orders_creation
        CHECK (creation_status IN ('CREATING', 'READY', 'FAILED')),
    CONSTRAINT ck_merchant_qr_orders_version CHECK (version >= 0)
);

CREATE INDEX ix_merchant_qr_orders_status
    ON merchant_qr_orders (provider_status, provider_expires_at, id);
