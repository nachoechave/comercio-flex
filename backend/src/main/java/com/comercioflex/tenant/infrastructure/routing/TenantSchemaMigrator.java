package com.comercioflex.tenant.infrastructure.routing;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaMigrator {

	private final TenantDatabaseProperties properties;

	public TenantSchemaMigrator(TenantDatabaseProperties properties) {
		this.properties = properties;
	}

	public void migrate(String url) {
		if (isBlank(properties.getMigrationUsername())
				|| isBlank(properties.getMigrationPassword())) {
			throw new IllegalStateException("Tenant migration credentials are required");
		}
		Flyway.configure()
			.dataSource(url, properties.getMigrationUsername(), properties.getMigrationPassword())
			.locations("classpath:db/migration/tenant")
			.load()
			.migrate();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
