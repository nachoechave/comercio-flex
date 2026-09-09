ALTER TABLE tenants
    ADD COLUMN tenant_type VARCHAR(30) NOT NULL DEFAULT 'ECOMMERCE',
    ADD CONSTRAINT ck_tenants_type CHECK (tenant_type IN ('ECOMMERCE', 'RADIO'));
