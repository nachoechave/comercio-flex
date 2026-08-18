package com.comercioflex.tenant.infrastructure.routing;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.DisposableBean;

import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class TenantDataSourceRegistry implements TenantConnectionCatalog, DisposableBean {

	private static final Pattern SAFE_DATABASE_KEY = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?");

	private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
	private final TenantRoutingDataSource routingDataSource;

	public TenantDataSourceRegistry(
			TenantContext tenantContext,
			TenantDatabaseProperties properties) {
		Map<String, TenantDatabaseProperties.ConnectionDetails> configuredConnections =
			properties.configuredTenantConnections();
		configuredConnections.forEach(this::validate);

		try {
			configuredConnections.forEach(this::register);
		}
		catch (RuntimeException exception) {
			dataSources.values().forEach(HikariDataSource::close);
			throw exception;
		}
		routingDataSource = new TenantRoutingDataSource(tenantContext, dataSources::get);
	}

	@Override
	public boolean contains(String databaseKey) {
		return dataSources.containsKey(databaseKey);
	}

	public DataSource routingDataSource() {
		return routingDataSource;
	}

	public void register(
			String databaseKey,
			TenantDatabaseProperties.ConnectionDetails details) {
		validate(databaseKey, details);
		if (dataSources.containsKey(databaseKey)) {
			return;
		}
		HikariDataSource candidate = createDataSource(databaseKey, details);
		try (Connection ignored = candidate.getConnection()) {
			HikariDataSource existing = dataSources.putIfAbsent(databaseKey, candidate);
			if (existing != null) {
				candidate.close();
			}
		}
		catch (SQLException exception) {
			candidate.close();
			throw new IllegalStateException("Tenant datasource validation failed", exception);
		}
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
