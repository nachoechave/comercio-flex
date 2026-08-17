ALTER TABLE tenants
    ADD COLUMN industry VARCHAR(100) NULL AFTER display_name,
    ADD COLUMN contact_phone VARCHAR(40) NULL AFTER industry,
    ADD COLUMN domain VARCHAR(253) NULL AFTER contact_phone,
    ADD CONSTRAINT uk_tenants_domain UNIQUE (domain);

ALTER TABLE tenants
    DROP CHECK ck_tenants_status,
    ADD CONSTRAINT ck_tenants_status CHECK (
        status IN (
            'ACTIVE', 'INACTIVE', 'PROVISIONING',
            'PROVISIONING_FAILED', 'SUSPENDED'
        )
    );

CREATE TABLE tenant_infrastructure (
    tenant_id BIGINT NOT NULL,
    database_name VARCHAR(64) NOT NULL,
    provisioning_status VARCHAR(30) NOT NULL,
    requested_tenant_status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(240) NULL,
    provisioned_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tenant_infrastructure PRIMARY KEY (tenant_id),
    CONSTRAINT uk_tenant_infrastructure_database UNIQUE (database_name),
    CONSTRAINT fk_tenant_infrastructure_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tenant_infrastructure_status CHECK (
        provisioning_status IN ('PENDING', 'READY', 'FAILED')
    ),
    CONSTRAINT ck_tenant_infrastructure_requested_status CHECK (
        requested_tenant_status IN ('ACTIVE', 'INACTIVE')
    )
);

CREATE INDEX ix_tenant_infrastructure_status
    ON tenant_infrastructure (provisioning_status);
