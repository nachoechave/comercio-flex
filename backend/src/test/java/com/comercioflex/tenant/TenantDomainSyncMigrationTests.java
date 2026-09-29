package com.comercioflex.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TenantDomainSyncMigrationTests {

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4.10");

	@Test
	void backfillsConfiguredDomainAsVerifiedPrimaryRoutingHostname() throws Exception {
		Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
			.locations("classpath:db/migration/control").target("18").load().migrate();

		try (var connection = DriverManager.getConnection(
				DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
				var sql = connection.createStatement()) {
			sql.executeUpdate("""
				INSERT INTO tenants (public_id, slug, display_name, status, database_key, domain)
				VALUES (UUID_TO_BIN(UUID()), 'limits', 'Limits', 'ACTIVE', 'tenant-limits', 'limits.com.ar')
				""");
			sql.executeUpdate("""
				INSERT INTO tenant_domains (tenant_id, hostname, primary_domain, verified)
				SELECT id, 'old.limits.example', TRUE, TRUE FROM tenants WHERE slug = 'limits'
				""");

			Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
				.locations("classpath:db/migration/control").load().migrate();

			try (var rows = sql.executeQuery("""
				SELECT domain.hostname, domain.primary_domain, domain.verified
				FROM tenant_domains domain
				JOIN tenants tenant ON tenant.id = domain.tenant_id
				WHERE tenant.slug = 'limits' AND domain.hostname = 'limits.com.ar'
				""")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getBoolean("primary_domain")).isTrue();
				assertThat(rows.getBoolean("verified")).isTrue();
			}

			try (var rows = sql.executeQuery("""
				SELECT primary_domain
				FROM tenant_domains domain
				JOIN tenants tenant ON tenant.id = domain.tenant_id
				WHERE tenant.slug = 'limits' AND domain.hostname = 'old.limits.example'
				""")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getBoolean("primary_domain")).isFalse();
			}
		}
	}
}
