package com.comercioflex.tenant.infrastructure.routing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.DisposableBean;

import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class TenantDataSourceRegistry implements TenantConnectionCatalog, DisposableBean {

	private static final Pattern SAFE_DATABASE_KEY = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?");

	private final Map<String, HikariDataSource> dataSources;
	private final TenantRoutingDataSource routingDataSource;

	public TenantDataSourceRegistry(
			TenantContext tenantContext,
			TenantDatabaseProperties properties) {
		properties.getTenantConnections().forEach(this::validate);

		Map<String, HikariDataSource> configuredDataSources = new LinkedHashMap<>();
		try {
			properties.getTenantConnections().forEach((databaseKey, details) ->
				configuredDataSources.put(databaseKey, createDataSource(databaseKey, details)));
		}
		catch (RuntimeException exception) {
			configuredDataSources.values().forEach(HikariDataSource::close);
			throw exception;
		}
		dataSources = Map.copyOf(configuredDataSources);

		Map<Object, Object> routingTargets = new LinkedHashMap<>();
		routingTargets.putAll(dataSources);
		routingDataSource = new TenantRoutingDataSource(tenantContext);
		routingDataSource.setTargetDataSources(routingTargets);
		routingDataSource.afterPropertiesSet();
	}

	@Override
	public boolean contains(String databaseKey) {
		return dataSources.containsKey(databaseKey);
	}

	public DataSource routingDataSource() {
		return routingDataSource;
	}

	@Override
	public void destroy() {
		dataSources.values().forEach(HikariDataSource::close);
	}

	private HikariDataSource createDataSource(
			String databaseKey,
			TenantDatabaseProperties.ConnectionDetails details) {
		HikariConfig config = new HikariConfig();
		config.setPoolName("tenant-" + databaseKey);
		config.setJdbcUrl(details.getUrl());
		config.setUsername(details.getUsername());
		config.setPassword(details.getPassword());
		config.addDataSourceProperty("connectionTimeZone", "UTC");
		config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
		config.setMaximumPoolSize(5);
		config.setMinimumIdle(0);
		config.setConnectionTimeout(3_000);
		return new HikariDataSource(config);
	}

	private void validate(
			String databaseKey,
			TenantDatabaseProperties.ConnectionDetails details) {
		if (!SAFE_DATABASE_KEY.matcher(databaseKey).matches()) {
			throw new IllegalStateException("Invalid tenant database key in configuration");
		}
		if (isBlank(details.getUrl()) || isBlank(details.getUsername()) || isBlank(details.getPassword())) {
			throw new IllegalStateException("Incomplete tenant database configuration for key " + databaseKey);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
