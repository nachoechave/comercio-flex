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
ON DUPLICATE KEY UPDATE
    checkout_enabled = TRUE;
