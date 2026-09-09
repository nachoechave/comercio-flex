package com.comercioflex.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TenantTypeMigrationTests {

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4.10");

	@Test
	void upgradesExistingTenantsAndConstrainsNewTypes() throws Exception {
		Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
			.locations("classpath:db/migration/control").target("15").load().migrate();
		try (var connection = DriverManager.getConnection(
				DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
				var sql = connection.createStatement()) {
			sql.executeUpdate("""
				INSERT INTO tenants (public_id, slug, display_name, status, database_key)
				VALUES (UUID_TO_BIN(UUID()), 'existing', 'Existing', 'ACTIVE', 'existing')
				""");
			Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
				.locations("classpath:db/migration/control").load().migrate();
			try (var rows = sql.executeQuery("SELECT tenant_type FROM tenants WHERE slug = 'existing'")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getString(1)).isEqualTo("ECOMMERCE");
			}
			for (String type : new String[] {"ECOMMERCE", "RADIO"}) {
				sql.executeUpdate("UPDATE tenants SET tenant_type = '" + type + "' WHERE slug = 'existing'");
				try (var rows = sql.executeQuery("SELECT tenant_type FROM tenants WHERE slug = 'existing'")) {
					assertThat(rows.next()).isTrue();
					assertThat(rows.getString(1)).isEqualTo(type);
				}
			}
			assertThatThrownBy(() -> sql.executeUpdate("UPDATE tenants SET tenant_type = 'UNKNOWN'"))
				.isInstanceOf(java.sql.SQLException.class);
			assertThatThrownBy(() -> sql.executeUpdate("UPDATE tenants SET tenant_type = NULL"))
				.isInstanceOf(java.sql.SQLException.class);
		}
	}
}
