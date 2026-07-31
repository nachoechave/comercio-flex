CREATE TABLE payment_oauth_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    tenant_id BIGINT NOT NULL,
    initiated_by_user_id BIGINT NOT NULL,
    initiated_by_user_public_id BINARY(16) NOT NULL,
    initiated_by_role VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    state_hash BINARY(32) NOT NULL,
    pkce_verifier_ciphertext VARBINARY(512) NULL,
    pkce_verifier_nonce BINARY(12) NULL,
    pkce_verifier_key_id VARCHAR(64) NULL,
    status VARCHAR(30) NOT NULL,
    active_attempt_slot TINYINT GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('PENDING', 'PROCESSING') THEN 1
            ELSE NULL
        END
    ) STORED,
    expires_at TIMESTAMP(6) NOT NULL,
    claimed_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    failure_code VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_oauth_attempts PRIMARY KEY (id),
    CONSTRAINT uk_payment_oauth_attempts_public_id UNIQUE (public_id),
    CONSTRAINT uk_payment_oauth_attempts_state_hash UNIQUE (state_hash),
    CONSTRAINT uk_payment_oauth_attempts_active UNIQUE (
        tenant_id,
        initiated_by_user_id,
        provider,
        environment,
        active_attempt_slot
    ),
    CONSTRAINT fk_payment_oauth_attempts_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_oauth_attempts_user FOREIGN KEY (initiated_by_user_id)
        REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_oauth_attempts_provider
        CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT ck_payment_oauth_attempts_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_payment_oauth_attempts_role
        CHECK (initiated_by_role = 'OWNER'),
    CONSTRAINT ck_payment_oauth_attempts_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED',
            'EXPIRED',
            'SUPERSEDED'
        )
    ),
    CONSTRAINT ck_payment_oauth_attempts_version CHECK (version >= 0),
    CONSTRAINT ck_payment_oauth_attempts_secret CHECK (
        (
            status IN ('PENDING', 'PROCESSING')
            AND pkce_verifier_ciphertext IS NOT NULL
            AND pkce_verifier_nonce IS NOT NULL
            AND pkce_verifier_key_id IS NOT NULL
        )
        OR
        (
            status IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'SUPERSEDED')
            AND pkce_verifier_ciphertext IS NULL
            AND pkce_verifier_nonce IS NULL
            AND pkce_verifier_key_id IS NULL
        )
    ),
    CONSTRAINT ck_payment_oauth_attempts_timestamps CHECK (
        (
            status = 'PENDING'
            AND claimed_at IS NULL
            AND completed_at IS NULL
        )
        OR
        (
            status = 'PROCESSING'
            AND claimed_at IS NOT NULL
            AND completed_at IS NULL
        )
        OR
        (
            status IN ('SUCCEEDED', 'FAILED')
            AND claimed_at IS NOT NULL
            AND completed_at IS NOT NULL
        )
        OR
        (
            status IN ('EXPIRED', 'SUPERSEDED')
            AND completed_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_payment_oauth_attempts_failure CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL)
    )
);

CREATE INDEX ix_payment_oauth_attempts_expiry
    ON payment_oauth_attempts (status, expires_at, id);
CREATE INDEX ix_payment_oauth_attempts_tenant_created
    ON payment_oauth_attempts (tenant_id, created_at DESC, id DESC);

CREATE TABLE merchant_payment_connections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    seller_account_id VARCHAR(100) NULL,
    seller_nickname VARCHAR(120) NULL,
    active_seller_account_id VARCHAR(100) GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('CONNECTED', 'REAUTHORIZATION_REQUIRED')
                THEN seller_account_id
            ELSE NULL
        END
    ) STORED,
    granted_scopes VARCHAR(255) NULL,
    access_token_ciphertext VARBINARY(4096) NULL,
    access_token_nonce BINARY(12) NULL,
    access_token_key_id VARCHAR(64) NULL,
    refresh_token_ciphertext VARBINARY(4096) NULL,
    refresh_token_nonce BINARY(12) NULL,
    refresh_token_key_id VARCHAR(64) NULL,
    access_token_expires_at TIMESTAMP(6) NULL,
    connected_by_user_id BIGINT NULL,
    connected_by_user_public_id BINARY(16) NULL,
    connected_by_role VARCHAR(30) NULL,
    oauth_attempt_public_id BINARY(16) NULL,
    connected_at TIMESTAMP(6) NULL,
    last_refreshed_at TIMESTAMP(6) NULL,
    disconnected_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merchant_payment_connections PRIMARY KEY (id),
    CONSTRAINT uk_merchant_payment_connections_public_id UNIQUE (public_id),
    CONSTRAINT uk_merchant_payment_connections_tenant UNIQUE (
        tenant_id,
        provider,
        environment
    ),
    CONSTRAINT uk_merchant_payment_connections_seller UNIQUE (
        provider,
        environment,
        active_seller_account_id
    ),
    CONSTRAINT fk_merchant_payment_connections_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_merchant_payment_connections_user FOREIGN KEY (connected_by_user_id)
        REFERENCES platform_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_merchant_payment_connections_provider
        CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT ck_merchant_payment_connections_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_merchant_payment_connections_status CHECK (
        status IN (
            'CONNECTED',
            'REAUTHORIZATION_REQUIRED',
            'DISCONNECTED'
        )
    ),
    CONSTRAINT ck_merchant_payment_connections_role CHECK (
        connected_by_role IS NULL OR connected_by_role = 'OWNER'
    ),
    CONSTRAINT ck_merchant_payment_connections_version CHECK (version >= 0),
    CONSTRAINT ck_merchant_payment_connections_tokens CHECK (
        (
            status = 'CONNECTED'
            AND access_token_ciphertext IS NOT NULL
            AND access_token_nonce IS NOT NULL
            AND access_token_key_id IS NOT NULL
            AND refresh_token_ciphertext IS NOT NULL
            AND refresh_token_nonce IS NOT NULL
            AND refresh_token_key_id IS NOT NULL
            AND access_token_expires_at IS NOT NULL
        )
        OR
        (
            status IN ('REAUTHORIZATION_REQUIRED', 'DISCONNECTED')
            AND access_token_ciphertext IS NULL
            AND access_token_nonce IS NULL
            AND access_token_key_id IS NULL
            AND refresh_token_ciphertext IS NULL
            AND refresh_token_nonce IS NULL
            AND refresh_token_key_id IS NULL
            AND access_token_expires_at IS NULL
        )
    ),
    CONSTRAINT ck_merchant_payment_connections_identity CHECK (
        (
            status IN ('CONNECTED', 'REAUTHORIZATION_REQUIRED')
            AND seller_account_id IS NOT NULL
            AND granted_scopes IS NOT NULL
            AND connected_at IS NOT NULL
            AND connected_by_user_public_id IS NOT NULL
            AND connected_by_role = 'OWNER'
            AND oauth_attempt_public_id IS NOT NULL
        )
        OR status = 'DISCONNECTED'
    ),
    CONSTRAINT ck_merchant_payment_connections_timestamps CHECK (
        (
            status <> 'DISCONNECTED'
            AND disconnected_at IS NULL
        )
        OR
        (
            status = 'DISCONNECTED'
            AND disconnected_at IS NOT NULL
        )
    )
);

CREATE INDEX ix_merchant_payment_connections_refresh
    ON merchant_payment_connections (status, access_token_expires_at, id);
CREATE INDEX ix_merchant_payment_connections_tenant_status
    ON merchant_payment_connections (tenant_id, status, id);

CREATE TABLE merchant_payment_connection_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    tenant_id BIGINT NOT NULL,
    connection_public_id BINARY(16) NOT NULL,
    oauth_attempt_public_id BINARY(16) NULL,
    provider VARCHAR(30) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_user_public_id BINARY(16) NULL,
    actor_role VARCHAR(30) NULL,
    reason_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merchant_payment_connection_events PRIMARY KEY (id),
    CONSTRAINT uk_merchant_payment_connection_events_public_id UNIQUE (public_id),
    CONSTRAINT fk_merchant_payment_connection_events_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_merchant_payment_connection_events_user FOREIGN KEY (actor_user_id)
        REFERENCES platform_users (id) ON DELETE SET NULL,
    CONSTRAINT ck_merchant_payment_connection_events_provider
        CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT ck_merchant_payment_connection_events_environment
        CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT ck_merchant_payment_connection_events_type CHECK (
        event_type IN (
            'CONNECTED',
            'REFRESHED',
            'REAUTHORIZATION_REQUIRED',
            'DISCONNECTED'
        )
    ),
    CONSTRAINT ck_merchant_payment_connection_events_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM', 'PROVIDER')),
    CONSTRAINT ck_merchant_payment_connection_events_actor CHECK (
        (
            actor_type = 'USER'
            AND actor_user_public_id IS NOT NULL
            AND actor_role = 'OWNER'
        )
        OR
        (
            actor_type IN ('SYSTEM', 'PROVIDER')
            AND actor_user_public_id IS NULL
            AND actor_role IS NULL
        )
    )
);

CREATE INDEX ix_merchant_payment_connection_events_tenant_created
    ON merchant_payment_connection_events (tenant_id, created_at DESC, id DESC);
CREATE INDEX ix_merchant_payment_connection_events_connection_created
    ON merchant_payment_connection_events (
        connection_public_id,
        created_at DESC,
        id DESC
    );
