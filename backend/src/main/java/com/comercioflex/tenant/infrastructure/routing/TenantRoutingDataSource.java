package com.comercioflex.tenant.infrastructure.routing;

import java.util.Map;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.comercioflex.tenant.application.TenantContext;

final class TenantRoutingDataSource extends AbstractRoutingDataSource {

	private final TenantContext tenantContext;
	private final Function<String, DataSource> dataSourceLookup;

	TenantRoutingDataSource(
			TenantContext tenantContext,
			Function<String, DataSource> dataSourceLookup) {
		this.tenantContext = tenantContext;
		this.dataSourceLookup = dataSourceLookup;
		setLenientFallback(false);
		setTargetDataSources(Map.of());
		afterPropertiesSet();
	}

	@Override
	protected Object determineCurrentLookupKey() {
		return tenantContext.currentDatabaseKey().orElse(null);
	}

	@Override
	protected DataSource determineTargetDataSource() {
		Object lookupKey = determineCurrentLookupKey();
		if (!(lookupKey instanceof String databaseKey)) {
			throw new IllegalStateException("No tenant database selected");
		}
		DataSource dataSource = dataSourceLookup.apply(databaseKey);
		if (dataSource == null) {
			throw new IllegalStateException("No datasource configured for selected tenant");
		}
		return dataSource;
	}
}
