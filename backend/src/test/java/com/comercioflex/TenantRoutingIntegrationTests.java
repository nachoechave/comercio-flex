package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.tenant.application.TenantContext;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TenantRoutingIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.10");
	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String PASSWORD_HASH =
		"{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

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
		registry.add("app.media.local-root", () -> "target/test-media-tenant-routing");
		registry.add("app.payments.checkout-pro.enabled", () -> "true");
		registry.add("app.payments.checkout-pro.test-access-token", () -> "TEST-token");
		registry.add("app.payments.checkout-pro.test-seller-account-id", () -> "seller-test");
		registry.add("app.payments.checkout-pro.test-demo-tenant-slug", () -> "tienda-a");
		registry.add("app.payments.checkout-pro.public-backend-base-uri",
			() -> "https://backend.example.test");
		registry.add("app.payments.checkout-pro.frontend-base-uri",
			() -> "https://frontend.example.test");
		registry.add("app.payments.checkout-pro.webhook-secret", () -> "test-webhook-secret");
		registerTenantConnection(registry, "tenant-a", TENANT_A_DATABASE);
		registerTenantConnection(registry, "tenant-b", TENANT_B_DATABASE);
	}

	@BeforeEach
	void seedDatabases() throws SQLException {
		execute(CONTROL_DATABASE, "DELETE FROM SPRING_SESSION_ATTRIBUTES");
		execute(CONTROL_DATABASE, "DELETE FROM SPRING_SESSION");
		execute(CONTROL_DATABASE, "DELETE FROM platform_audit_events");
		execute(CONTROL_DATABASE, "DELETE FROM merchant_payment_capabilities");
		execute(CONTROL_DATABASE, "DELETE FROM memberships");
		execute(CONTROL_DATABASE, "DELETE FROM platform_users");
		execute(CONTROL_DATABASE, "DELETE FROM tenants");
		execute(CONTROL_DATABASE, tenantInsert("tienda-a", "ACTIVE", "tenant-a"));
		execute(CONTROL_DATABASE, tenantInsert("tienda-b", "ACTIVE", "tenant-b"));
		execute(CONTROL_DATABASE, tenantInsert("tienda-inactiva", "INACTIVE", "tenant-inactive"));
		execute(CONTROL_DATABASE, tenantInsert("tienda-sin-conexion", "ACTIVE", "tenant-c"));
		execute(CONTROL_DATABASE, """
			INSERT INTO platform_users (
				public_id, email_normalized, display_name, password_hash, status, platform_role
			)
			VALUES
				(UUID_TO_BIN(UUID()), 'superadmin@example.com', 'Super Admin', '%s',
					'ACTIVE', 'SUPER_ADMIN'),
				(UUID_TO_BIN(UUID()), 'owner@example.com', 'Tenant Owner', '%s',
					'ACTIVE', 'USER')
			""".formatted(PASSWORD_HASH, PASSWORD_HASH));
		execute(CONTROL_DATABASE, """
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, 'OWNER', 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = 'owner@example.com' AND tenant.slug = 'tienda-a'
			""");

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
	void superAdminBrandingIsAuditedPublicAndStrictlyTenantScoped() throws Exception {
		AuthenticatedCookies superAdmin = login("superadmin@example.com");
		String companyId = queryString(CONTROL_DATABASE, """
			SELECT BIN_TO_UUID(public_id) FROM tenants WHERE slug = 'tienda-a'
			""");
		String update = """
			{
			  "primaryColor":"#123456",
			  "secondaryColor":"#654321",
			  "backgroundColor":"#FAFAFA",
			  "textColor":"#101010",
			  "font":"SERIF",
			  "heroTitle":"Colección A",
			  "heroSubtitle":"Solo para el tenant A",
			  "template":"MODERN"
			}
			""";

		mockMvc.perform(put("/api/v1/superadmin/companies/{id}/branding", companyId)
				.cookie(superAdmin.sessionCookie(), superAdmin.csrfCookie())
				.header("X-XSRF-TOKEN", superAdmin.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(update))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.primaryColor").value("#123456"))
			.andExpect(jsonPath("$.template").value("MODERN"));

		MockMultipartFile logo = new MockMultipartFile(
			"file", "logo.png", "image/png", png());
		mockMvc.perform(multipart(
				"/api/v1/superadmin/companies/{id}/branding/assets/logo", companyId)
				.file(logo)
				.with(request -> { request.setMethod("PUT"); return request; })
				.cookie(superAdmin.sessionCookie(), superAdmin.csrfCookie())
				.header("X-XSRF-TOKEN", superAdmin.csrfCookie().getValue()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logoUrl").value(org.hamcrest.Matchers.containsString(
				"/api/v1/stores/tienda-a/media/branding/logo?v=")));

		mockMvc.perform(get("/api/v1/stores/tienda-a/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.branding.primaryColor").value("#123456"))
			.andExpect(jsonPath("$.branding.heroTitle").value("Colección A"))
			.andExpect(jsonPath("$.branding.logoUrl").isNotEmpty());
		mockMvc.perform(get("/api/v1/stores/tienda-a/media/branding/logo"))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentType())
				.isEqualTo("image/png"));

		mockMvc.perform(get("/api/v1/stores/tienda-b/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.branding.primaryColor").value("#6D3CE7"))
			.andExpect(jsonPath("$.branding.logoUrl").isEmpty());
		mockMvc.perform(get("/api/v1/stores/tienda-b/media/branding/logo"))
			.andExpect(status().isNotFound());

		assertThat(queryInt(CONTROL_DATABASE, """
			SELECT COUNT(*) FROM platform_audit_events
			WHERE action_name IN ('COMPANY_BRANDING_UPDATED', 'COMPANY_BRANDING_ASSET_UPDATED')
			""")).isEqualTo(2);
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

	@Test
	void paymentMethodsResolveTenantSettingsAndCommercialCapability() throws Exception {
		execute(CONTROL_DATABASE, """
			INSERT INTO merchant_payment_capabilities (
				tenant_id, environment, checkout_enabled
			)
			SELECT id, 'TEST', TRUE FROM tenants WHERE slug = 'tienda-a'
			""");

		mockMvc.perform(get("/api/v1/stores/tienda-a/payment-methods"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mercadoPago").value(true))
			.andExpect(jsonPath("$.bankTransfer").value(false));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();

		execute(TENANT_A_DATABASE, """
			UPDATE store_settings
			SET bank_transfer_enabled = TRUE,
				bank_account_holder = 'Tienda A SA', bank_alias = 'TIENDA.A'
			""");

		mockMvc.perform(get("/api/v1/stores/tienda-a/payment-methods"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mercadoPago").value(true))
			.andExpect(jsonPath("$.bankTransfer").value(true));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();

		execute(CONTROL_DATABASE, """
			UPDATE merchant_payment_capabilities capability
			JOIN tenants tenant ON tenant.id = capability.tenant_id
			SET capability.checkout_enabled = FALSE
			WHERE tenant.slug = 'tienda-a' AND capability.environment = 'TEST'
			""");

		mockMvc.perform(get("/api/v1/stores/tienda-a/payment-methods"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mercadoPago").value(false))
			.andExpect(jsonPath("$.bankTransfer").value(true));
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void recordsAuthenticatedTenantAdministrationAsOperationalActivity() throws Exception {
		AuthenticatedCookies owner = login("owner@example.com");

		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/settings")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storeName").value("Tienda A"));

		assertThat(queryInt(CONTROL_DATABASE, """
			SELECT COUNT(*) FROM tenants
			WHERE slug = 'tienda-a' AND last_activity_at IS NOT NULL
			""")).isEqualTo(1);
		assertThat(queryInt(CONTROL_DATABASE, """
			SELECT COUNT(*) FROM tenants
			WHERE slug = 'tienda-b' AND last_activity_at IS NOT NULL
			""")).isZero();
	}

	private AuthenticatedCookies login(String email) throws Exception {
		Cookie csrf = requiredCookie(mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk()).andReturn().getResponse(), "XSRF-TOKEN");
		MockHttpServletResponse response = mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders
					.post("/api/v1/auth/login")
				.cookie(csrf)
				.header("X-XSRF-TOKEN", csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse();
		return new AuthenticatedCookies(
			requiredCookie(response, "CFSESSION"),
			requiredCookie(response, "XSRF-TOKEN"));
	}

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) throw new AssertionError("Missing response cookie " + name);
		return cookie;
	}

	private static byte[] png() throws Exception {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(18, 52, 86));
			graphics.fillRect(0, 0, 4, 4);
		}
		finally {
			graphics.dispose();
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private static String queryString(MySQLContainer<?> database, String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(), database.getUsername(), database.getPassword());
				Statement statement = connection.createStatement();
				var result = statement.executeQuery(sql)) {
			if (!result.next()) throw new AssertionError("Query returned no rows");
			return result.getString(1);
		}
	}

	private static int queryInt(MySQLContainer<?> database, String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(), database.getUsername(), database.getPassword());
				Statement statement = connection.createStatement();
				var result = statement.executeQuery(sql)) {
			if (!result.next()) throw new AssertionError("Query returned no rows");
			return result.getInt(1);
		}
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

	private record AuthenticatedCookies(Cookie sessionCookie, Cookie csrfCookie) {
	}
}
