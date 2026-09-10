ALTER TABLE platform_users
    ADD COLUMN first_name VARCHAR(70) NULL,
    ADD COLUMN last_name VARCHAR(70) NULL,
    ADD COLUMN phone VARCHAR(40) NULL;

CREATE TABLE identity_password_resets (
    token_hash BINARY(32) NOT NULL,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_identity_password_resets PRIMARY KEY (token_hash),
    CONSTRAINT fk_identity_reset_user FOREIGN KEY (user_id)
        REFERENCES platform_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_identity_reset_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX ix_identity_reset_expiry ON identity_password_resets (expires_at);
CREATE INDEX ix_identity_reset_user ON identity_password_resets (user_id);
