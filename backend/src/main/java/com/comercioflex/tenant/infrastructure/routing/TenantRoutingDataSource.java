package com.comercioflex.tenant.infrastructure.routing;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.comercioflex.tenant.application.TenantContext;

final class TenantRoutingDataSource extends AbstractRoutingDataSource {

	private final TenantContext tenantContext;

	TenantRoutingDataSource(TenantContext tenantContext) {
		this.tenantContext = tenantContext;
		setLenientFallback(false);
	}

	@Override
	protected Object determineCurrentLookupKey() {
		return tenantContext.currentDatabaseKey().orElse(null);
	}
}
