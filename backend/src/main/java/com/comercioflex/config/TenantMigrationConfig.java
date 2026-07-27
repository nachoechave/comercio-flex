package com.comercioflex.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import com.comercioflex.tenant.infrastructure.routing.TenantDatabaseProperties;

@Configuration
public class TenantMigrationConfig {

	@Bean
	@DependsOn("flyway")
	@ConditionalOnProperty(
		name = "app.database.tenant-migration-enabled",
		havingValue = "true"
	)
	InitializingBean migrateKnownTenantDatabases(TenantDatabaseProperties properties) {
		return () -> {
			requireMigrationCredentials(properties);
			properties.getTenantConnections().values().forEach(details -> Flyway.configure()
				.dataSource(
					details.getUrl(),
					properties.getMigrationUsername(),
					properties.getMigrationPassword())
				.locations("classpath:db/migration/tenant")
				.load()
				.migrate());
		};
	}

	private void requireMigrationCredentials(TenantDatabaseProperties properties) {
		if (properties.getMigrationUsername() == null
				|| properties.getMigrationUsername().isBlank()
				|| properties.getMigrationPassword() == null
				|| properties.getMigrationPassword().isBlank()) {
			throw new IllegalStateException("Tenant migration credentials are required");
		}
	}
}
