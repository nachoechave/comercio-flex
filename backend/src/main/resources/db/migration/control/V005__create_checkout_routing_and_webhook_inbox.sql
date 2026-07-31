CREATE TABLE merchant_payment_capabilities (
    tenant_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    checkout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merchant_payment_capabilities
        PRIMARY KEY (tenant_id, environment),
    CONSTRAINT fk_merchant_payment_capabilities_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchant_payment_capabilities_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_merchant_payment_capabilities_enabled
        CHECK (checkout_enabled IN (FALSE, TRUE))
);

CREATE TABLE payment_webhook_routes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    route_token_hash BINARY(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    payment_intent_public_id BINARY(16) NOT NULL,
    expected_seller_account_id VARCHAR(100) NOT NULL,
    provider_preference_id VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_webhook_routes PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_routes_public_id UNIQUE (public_id),
    CONSTRAINT uk_payment_webhook_routes_token UNIQUE (route_token_hash),
    CONSTRAINT uk_payment_webhook_routes_intent UNIQUE (
        tenant_id, environment, payment_intent_public_id
    ),
    CONSTRAINT fk_payment_webhook_routes_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_webhook_routes_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_payment_webhook_routes_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED'))
);

CREATE INDEX ix_payment_webhook_routes_expiry
    ON payment_webhook_routes (status, expires_at, id);

CREATE TABLE payment_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    route_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    notification_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    action_name VARCHAR(80) NOT NULL,
    provider_resource_id VARCHAR(100) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    live_mode BOOLEAN NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL,
    leased_until TIMESTAMP(6) NULL,
    processed_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_events_public_id UNIQUE (public_id),
    CONSTRAINT uk_payment_webhook_events_notification UNIQUE (
        provider, environment, notification_id
    ),
    CONSTRAINT fk_payment_webhook_events_route FOREIGN KEY (route_id)
        REFERENCES payment_webhook_routes (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_webhook_events_provider
        CHECK (provider = 'MERCADO_PAGO'),
    CONSTRAINT ck_payment_webhook_events_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_payment_webhook_events_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'RETRY', 'PROCESSED', 'DEAD')),
    CONSTRAINT ck_payment_webhook_events_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_payment_webhook_events_live CHECK (live_mode IN (FALSE, TRUE)),
    CONSTRAINT ck_payment_webhook_events_processing CHECK (
        (status = 'PROCESSING' AND leased_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND leased_until IS NULL)
    ),
    CONSTRAINT ck_payment_webhook_events_processed CHECK (
        (status = 'PROCESSED' AND processed_at IS NOT NULL)
        OR (status <> 'PROCESSED' AND processed_at IS NULL)
    ),
    CONSTRAINT ck_payment_webhook_events_error CHECK (
        (status IN ('RETRY', 'DEAD') AND last_error_code IS NOT NULL)
        OR (status NOT IN ('RETRY', 'DEAD') AND last_error_code IS NULL)
    )
);

CREATE INDEX ix_payment_webhook_events_work
    ON payment_webhook_events (status, available_at, leased_until, id);
