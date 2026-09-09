package com.comercioflex.tenant.infrastructure.control;

import com.comercioflex.tenant.domain.TenantType;

public record ActiveTenant(Long id, String slug, String displayName, String databaseKey,
	TenantType tenantType) {

	public ActiveTenant(Long id, String slug, String displayName, String databaseKey) {
		this(id, slug, displayName, databaseKey, TenantType.ECOMMERCE);
	}
}
