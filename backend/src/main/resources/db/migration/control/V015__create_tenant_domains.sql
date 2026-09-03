CREATE TABLE tenant_domains (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    hostname VARCHAR(253) NOT NULL,
    primary_domain BOOLEAN NOT NULL DEFAULT FALSE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_tenant_domains
        PRIMARY KEY (id),

    CONSTRAINT uk_tenant_domains_hostname
        UNIQUE (hostname),

    CONSTRAINT fk_tenant_domains_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_tenant_domains_tenant_id
    ON tenant_domains (tenant_id);