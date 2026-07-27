package com.comercioflex.config;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class TenantMigrationConfig {

	@Bean
	@DependsOn("flyway")
	@ConditionalOnProperty(
		name = "app.database.tenant-migration-enabled",
		havingValue = "true"
	)
	InitializingBean migrateKnownTenantDatabases(
			@Value("${app.database.tenant-urls}") String tenantUrls,
			@Value("${app.database.migration-username}") String username,
			@Value("${app.database.migration-password}") String password) {
		List<String> urls = Arrays.stream(tenantUrls.split(","))
			.map(String::trim)
			.filter(url -> !url.isBlank())
			.toList();

		return () -> urls.forEach(url -> Flyway.configure()
			.dataSource(url, username, password)
			.locations("classpath:db/migration/tenant")
			.load()
			.migrate());
	}
}
