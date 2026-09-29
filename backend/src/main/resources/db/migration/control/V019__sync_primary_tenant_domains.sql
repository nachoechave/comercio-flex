-- Keep the legacy tenants.domain field and the hostname routing table aligned.
-- Domains configured by a SUPER_ADMIN are trusted platform configuration and
-- become the verified primary hostname used by storefront routing.

UPDATE tenant_domains domain
JOIN tenants tenant ON tenant.id = domain.tenant_id
SET domain.primary_domain = FALSE
WHERE tenant.domain IS NOT NULL
  AND TRIM(tenant.domain) <> ''
  AND domain.primary_domain = TRUE
  AND domain.hostname <> LOWER(TRIM(tenant.domain));

UPDATE tenant_domains domain
JOIN tenants tenant
  ON tenant.id = domain.tenant_id
 AND domain.hostname = LOWER(TRIM(tenant.domain))
SET domain.primary_domain = TRUE,
    domain.verified = TRUE
WHERE tenant.domain IS NOT NULL
  AND TRIM(tenant.domain) <> '';

INSERT INTO tenant_domains (tenant_id, hostname, primary_domain, verified)
SELECT tenant.id, LOWER(TRIM(tenant.domain)), TRUE, TRUE
FROM tenants tenant
WHERE tenant.domain IS NOT NULL
  AND TRIM(tenant.domain) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM tenant_domains domain
    WHERE domain.hostname = LOWER(TRIM(tenant.domain))
  );
