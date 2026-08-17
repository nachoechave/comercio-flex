package com.comercioflex.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

import com.comercioflex.tenant.infrastructure.routing.ManagedTenantConnectionFactory;
import com.comercioflex.tenant.infrastructure.routing.TenantDataSourceRegistry;
import com.comercioflex.tenant.infrastructure.routing.TenantDatabaseProperties;
import com.comercioflex.tenant.infrastructure.routing.TenantSchemaMigrator;

@Configuration
public class TenantMigrationConfig {

	@Bean
	@DependsOn("flyway")
	InitializingBean migrateKnownTenantDatabases(
			TenantDatabaseProperties properties,
			TenantSchemaMigrator migrator,
			ManagedTenantConnectionFactory connectionFactory,
			@Qualifier("controlJdbcTemplate") JdbcTemplate controlJdbcTemplate) {
		return () -> {
			if (!properties.isTenantMigrationEnabled()) {
				return;
			}
			properties.getTenantConnections().values().forEach(details ->
				migrator.migrate(details.getUrl()));
			controlJdbcTemplate.queryForList("""
				SELECT database_name
				FROM tenant_infrastructure
				WHERE provisioning_status = 'READY'
				""", String.class).forEach(databaseName ->
				migrator.migrate(connectionFactory.urlFor(databaseName)));
		};
	}

	@Bean
	@DependsOn("migrateKnownTenantDatabases")
	InitializingBean registerManagedTenantDatabases(
			TenantDataSourceRegistry registry,
			ManagedTenantConnectionFactory connectionFactory,
			@Qualifier("controlJdbcTemplate") JdbcTemplate controlJdbcTemplate) {
		return () -> controlJdbcTemplate.queryForList("""
			SELECT tenant.database_key, infrastructure.database_name
			FROM tenant_infrastructure infrastructure
			JOIN tenants tenant ON tenant.id = infrastructure.tenant_id
			WHERE infrastructure.provisioning_status = 'READY'
			""").forEach(row -> registry.register(
			(String) row.get("database_key"),
			connectionFactory.applicationDetails((String) row.get("database_name"))));
	}
}
