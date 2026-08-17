ALTER TABLE tenants
    ADD COLUMN last_activity_at TIMESTAMP(6) NULL AFTER tenant_schema_version;

CREATE INDEX ix_tenants_last_activity
    ON tenants (last_activity_at DESC);
