CREATE TABLE merchant_qr_configurations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    provider_store_id VARCHAR(100) NULL,
    external_store_id VARCHAR(60) NOT NULL,
    provider_pos_id VARCHAR(100) NULL,
    external_pos_id VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    authorization_status VARCHAR(30) NOT NULL,
    pos_idempotency_key BINARY(16) NOT NULL,
    verification_started_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merchant_qr_configurations PRIMARY KEY (id),
    CONSTRAINT uk_merchant_qr_configurations_public_id UNIQUE (public_id),
    CONSTRAINT uk_merchant_qr_configurations_tenant UNIQUE (
        tenant_id,
        provider,
        environment
    ),
    CONSTRAINT uk_merchant_qr_configurations_store_external UNIQUE (
        provider,
        environment,
        external_store_id
    ),
    CONSTRAINT uk_merchant_qr_configurations_pos_external UNIQUE (
        provider,
        environment,
        external_pos_id
    ),
    CONSTRAINT fk_merchant_qr_configurations_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchant_qr_configurations_provider
        CHECK (provider = 'MERCADO_PAGO'),
    CONSTRAINT ck_merchant_qr_configurations_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_merchant_qr_configurations_status CHECK (
        status IN ('NO_CONFIGURADO', 'VERIFICANDO', 'LISTO', 'ERROR')
    ),
    CONSTRAINT ck_merchant_qr_configurations_authorization CHECK (
        authorization_status IN (
            'NOT_CHECKED',
            'AUTHORIZED',
            'UNAUTHORIZED_SCOPES',
            'NOT_FOUND',
            'PROVIDER_ERROR'
        )
    ),
    CONSTRAINT ck_merchant_qr_configurations_ready CHECK (
        status <> 'LISTO'
        OR (
            provider_store_id IS NOT NULL
            AND provider_pos_id IS NOT NULL
            AND authorization_status = 'AUTHORIZED'
        )
    ),
    CONSTRAINT ck_merchant_qr_configurations_verification CHECK (
        (status = 'VERIFICANDO' AND verification_started_at IS NOT NULL)
        OR (status <> 'VERIFICANDO' AND verification_started_at IS NULL)
    ),
    CONSTRAINT ck_merchant_qr_configurations_version CHECK (version >= 0)
);

CREATE INDEX ix_merchant_qr_configurations_status
    ON merchant_qr_configurations (status, verification_started_at, id);
