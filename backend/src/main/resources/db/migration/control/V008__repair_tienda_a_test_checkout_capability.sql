-- V007 could run before the demo tenant was provisioned in an existing environment.
-- Reconcile the explicit TEST capability now that tienda-a is registered.
INSERT INTO merchant_payment_capabilities (
    tenant_id,
    environment,
    checkout_enabled
)
SELECT
    tenant.id,
    'TEST',
    TRUE
FROM tenants tenant
WHERE tenant.slug = 'tienda-a'
    AND tenant.database_key = 'tenant-a'
    AND tenant.status = 'ACTIVE'
ON DUPLICATE KEY UPDATE
    checkout_enabled = TRUE;
