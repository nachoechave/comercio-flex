package com.comercioflex.tenant.application;

import com.comercioflex.tenant.domain.TenantType;

public record ResolvedTenant(
	Long id,
	String slug,
	String displayName,
	String databaseKey,
	TenantType tenantType) {

	public ResolvedTenant(Long id, String slug, String displayName, String databaseKey) {
		this(id, slug, displayName, databaseKey, TenantType.ECOMMERCE);
	}
}
