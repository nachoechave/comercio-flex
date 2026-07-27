package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.tenant.application.TenantContext;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TenantRoutingIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.10");

	@Container
	static final MySQLContainer<?> CONTROL_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);

	@Container
	static final MySQLContainer<?> TENANT_A_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);

	@Container
	static final MySQLContainer<?> TENANT_B_DATABASE = new MySQLContainer<>(MYSQL_IMAGE);

	static {
		Startables.deepStart(Stream.of(
			CONTROL_DATABASE,
			TENANT_A_DATABASE,
			TENANT_B_DATABASE)).join();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantContext tenantContext;

	@Autowired
	@Qualifier("tenantJdbcTemplate")
	private JdbcTemplate tenantJdbcTemplate;

	@DynamicPropertySource
	static void configureDatabases(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", CONTROL_DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", CONTROL_DATABASE::getUsername);
		registry.add("spring.datasource.password", CONTROL_DATABASE::getPassword);
		registry.add("spring.flyway.user", CONTROL_DATABASE::getUsername);
		registry.add("spring.flyway.password", CONTROL_DATABASE::getPassword);

		registry.add("app.database.tenant-migration-enabled", () -> "true");
		registry.add("app.database.migration-username", TENANT_A_DATABASE::getUsername);
		registry.add("app.database.migration-password", TENANT_A_DATABASE::getPassword);
		registerTenantConnection(registry, "tenant-a", TENANT_A_DATABASE);
		registerTenantConnection(registry, "tenant-b", TENANT_B_DATABASE);
	}

	@BeforeEach
	void seedDatabases() throws SQLException {
		execute(CONTROL_DATABASE, "DELETE FROM tenants");
		execute(CONTROL_DATABASE, tenantInsert("tienda-a", "ACTIVE", "tenant-a"));
		execute(CONTROL_DATABASE, tenantInsert("tienda-b", "ACTIVE", "tenant-b"));
		execute(CONTROL_DATABASE, tenantInsert("tienda-inactiva", "INACTIVE", "tenant-inactive"));
		execute(CONTROL_DATABASE, tenantInsert("tienda-sin-conexion", "ACTIVE", "tenant-c"));

		execute(TENANT_A_DATABASE, "DELETE FROM store_settings");
		execute(TENANT_A_DATABASE, """
			INSERT INTO store_settings (store_name, currency_code, timezone)
			VALUES ('Tienda A', 'ARS', 'America/Argentina/Buenos_Aires')
			""");
		execute(TENANT_B_DATABASE, "DELETE FROM store_settings");
		execute(TENANT_B_DATABASE, """
			INSERT INTO store_settings (store_name, currency_code, timezone)
			VALUES ('Tienda B', 'USD', 'America/Montevideo')
			""");
	}

	@Test
	void routesEachSlugToItsOwnDatabaseAndCleansTheContext() throws Exception {
		mockMvc.perform(get("/api/v1/stores/tienda-a/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("tienda-a"))
			.andExpect(jsonPath("$.storeName").value("Tienda A"))
			.andExpect(jsonPath("$.currencyCode").value("ARS"));

		assertThat(tenantContext.currentDatabaseKey()).isEmpty();

		mockMvc.perform(get("/api/v1/stores/tienda-b/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("tienda-b"))
			.andExpect(jsonPath("$.storeName").value("Tienda B"))
			.andExpect(jsonPath("$.currencyCode").value("USD"));

		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void ignoresClientAttemptsToChooseAnotherDatabase() throws Exception {
		mockMvc.perform(get("/api/v1/stores/tienda-a/settings")
				.header("X-Database-Key", "tenant-b")
				.queryParam("database_key", "tenant-b"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storeName").value("Tienda A"));
	}

	@Test
	void hidesUnknownInactiveAndUnconfiguredStores() throws Exception {
		assertStoreNotFound("no-existe");
		assertStoreNotFound("tienda-inactiva");
		assertStoreNotFound("tienda-sin-conexion");
	}

	@Test
	void failsClosedWhenNoTenantContextExists() {
		assertThatThrownBy(() -> tenantJdbcTemplate.queryForObject(
			"SELECT store_name FROM store_settings LIMIT 1",
			String.class))
			.isInstanceOf(CannotGetJdbcConnectionException.class)
			.hasRootCauseInstanceOf(IllegalStateException.class);
	}

	@Test
	void cleansTheContextAfterAnExceptionBeforeServingAnotherStore() throws Exception {
		execute(TENANT_A_DATABASE, "DELETE FROM store_settings");

		mockMvc.perform(get("/api/v1/stores/tienda-a/settings"))
			.andExpect(status().isNotFound());
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();

		mockMvc.perform(get("/api/v1/stores/tienda-b/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storeName").value("Tienda B"));
	}

	@Test
	void keepsConcurrentRequestsIsolated() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			List<Callable<String>> requests = IntStream.range(0, 24)
				.mapToObj(index -> (Callable<String>) () -> {
					String slug = index % 2 == 0 ? "tienda-a" : "tienda-b";
					return mockMvc.perform(get("/api/v1/stores/{slug}/settings", slug))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsString();
				})
				.toList();

			List<Future<String>> responses = executor.invokeAll(requests);
			for (int index = 0; index < responses.size(); index++) {
				String expectedStore = index % 2 == 0 ? "Tienda A" : "Tienda B";
				assertThat(responses.get(index).get()).contains(expectedStore);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void assertStoreNotFound(String slug) throws Exception {
		mockMvc.perform(get("/api/v1/stores/{slug}/settings", slug))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"))
			.andExpect(jsonPath("$.detail")
				.value("No existe una tienda activa para la dirección solicitada."));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	private static void registerTenantConnection(
			DynamicPropertyRegistry registry,
			String databaseKey,
			MySQLContainer<?> database) {
		String prefix = "app.database.tenant-connections." + databaseKey;
		registry.add(prefix + ".url", database::getJdbcUrl);
		registry.add(prefix + ".username", database::getUsername);
		registry.add(prefix + ".password", database::getPassword);
	}

	private static String tenantInsert(String slug, String status, String databaseKey) {
		return """
			INSERT INTO tenants (
				public_id,
				slug,
				display_name,
				status,
				database_key
			)
			VALUES (
				UNHEX(REPLACE(UUID(), '-', '')),
				'%s',
				'%s',
				'%s',
				'%s'
			)
			""".formatted(slug, slug, status, databaseKey);
	}

	private static void execute(MySQLContainer<?> database, String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(),
				database.getUsername(),
				database.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
