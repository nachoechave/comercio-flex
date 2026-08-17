ALTER TABLE platform_users
    ADD COLUMN platform_role VARCHAR(30) NOT NULL DEFAULT 'USER' AFTER status,
    ADD CONSTRAINT ck_platform_users_platform_role
        CHECK (platform_role IN ('USER', 'SUPER_ADMIN'));

ALTER TABLE tenants
    DROP CHECK ck_tenants_status,
    ADD CONSTRAINT ck_tenants_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'PROVISIONING', 'SUSPENDED'));

CREATE TABLE platform_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    action_name VARCHAR(80) NOT NULL,
    metadata JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_platform_audit_events PRIMARY KEY (id),
    CONSTRAINT uk_platform_audit_events_public_id UNIQUE (public_id),
    CONSTRAINT fk_platform_audit_events_actor FOREIGN KEY (actor_user_id)
        REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_audit_events_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT
);

CREATE INDEX ix_platform_audit_events_tenant_created
    ON platform_audit_events (tenant_id, created_at DESC, id DESC);

CREATE INDEX ix_platform_audit_events_actor_created
    ON platform_audit_events (actor_user_id, created_at DESC, id DESC);
