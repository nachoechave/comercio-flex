CREATE TABLE platform_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    password_changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_users PRIMARY KEY (id),
    CONSTRAINT uk_platform_users_public_id UNIQUE (public_id),
    CONSTRAINT uk_platform_users_email UNIQUE (email_normalized),
    CONSTRAINT ck_platform_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_memberships PRIMARY KEY (id),
    CONSTRAINT uk_memberships_user_tenant UNIQUE (user_id, tenant_id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id)
        REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'STAFF')),
    CONSTRAINT ck_memberships_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_memberships_tenant_status ON memberships (tenant_id, status);
CREATE INDEX ix_memberships_user_status ON memberships (user_id, status);
