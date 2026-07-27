CREATE TABLE tenants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    database_key VARCHAR(100) NOT NULL,
    tenant_schema_version VARCHAR(50) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_public_id UNIQUE (public_id),
    CONSTRAINT uk_tenants_slug UNIQUE (slug),
    CONSTRAINT uk_tenants_database_key UNIQUE (database_key),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PROVISIONING'))
);
