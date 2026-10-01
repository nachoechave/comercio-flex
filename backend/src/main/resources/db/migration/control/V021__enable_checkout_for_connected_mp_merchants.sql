-- Hotfix: habilita Checkout Pro en producción para comercios que ya
-- completaron correctamente la conexión OAuth con Mercado Pago.
INSERT INTO merchant_payment_capabilities (
    tenant_id,
    environment,
    checkout_enabled
)
SELECT
    tenant.id,
    'PRODUCTION',
    TRUE
FROM tenants tenant
JOIN merchant_payment_connections connection
    ON connection.tenant_id = tenant.id
WHERE tenant.status = 'ACTIVE'
    AND connection.provider = 'MERCADO_PAGO'
    AND connection.environment = 'PRODUCTION'
    AND connection.status = 'CONNECTED'
ON DUPLICATE KEY UPDATE
    checkout_enabled = TRUE;
