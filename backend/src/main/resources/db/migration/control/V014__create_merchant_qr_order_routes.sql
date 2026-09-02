CREATE TABLE merchant_qr_order_routes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    payment_intent_public_id BINARY(16) NOT NULL,
    provider_order_id VARCHAR(100) NOT NULL,
    expected_seller_account_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    available_at TIMESTAMP(6) NOT NULL,
    leased_until TIMESTAMP(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merchant_qr_order_routes PRIMARY KEY (id),
    CONSTRAINT uk_merchant_qr_order_routes_public_id UNIQUE (public_id),
    CONSTRAINT uk_merchant_qr_order_routes_intent UNIQUE (
        tenant_id, environment, payment_intent_public_id
    ),
    CONSTRAINT uk_merchant_qr_order_routes_provider_order UNIQUE (
        provider, environment, provider_order_id
    ),
    CONSTRAINT fk_merchant_qr_order_routes_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchant_qr_order_routes_provider
        CHECK (provider = 'MERCADO_PAGO'),
    CONSTRAINT ck_merchant_qr_order_routes_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_merchant_qr_order_routes_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT ck_merchant_qr_order_routes_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_merchant_qr_order_routes_lease CHECK (
        (status = 'ACTIVE') OR leased_until IS NULL
    )
);

CREATE INDEX ix_merchant_qr_order_routes_work
    ON merchant_qr_order_routes (status, available_at, leased_until, id);
