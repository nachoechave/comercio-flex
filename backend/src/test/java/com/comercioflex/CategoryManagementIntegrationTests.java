package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CategoryManagementIntegrationTests {

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
	private DataSource controlDataSource;

	@Autowired
	private ObjectMapper objectMapper;

	private JdbcTemplate control;

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
		registerTenant(registry, "tenant-a", TENANT_A_DATABASE);
		registerTenant(registry, "tenant-b", TENANT_B_DATABASE);
	}

	@BeforeEach
	void seedDatabases() throws SQLException {
		control = new JdbcTemplate(controlDataSource);
		control.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		control.update("DELETE FROM SPRING_SESSION");
		control.update("DELETE FROM memberships");
		control.update("DELETE FROM platform_users");
		control.update("DELETE FROM tenants");
		control.update("""
			INSERT INTO tenants
				(public_id, slug, display_name, status, database_key)
			VALUES
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		insertUser("owner@example.com", "Owner", "OWNER", true);
		insertUser("admin@example.com", "Admin", "ADMIN", false);
		insertUser("staff@example.com", "Staff", "STAFF", false);

		execute(TENANT_A_DATABASE, "DELETE FROM categories");
		execute(TENANT_B_DATABASE, "DELETE FROM categories");
	}

	@Test
	void ownerCanCreateRenameArchiveReactivateAndFilterWithoutChangingTheSlug()
			throws Exception {
		AuthenticatedCookies owner = login("owner@example.com");

		MockHttpServletResponse created = mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  Reméras   de Niño  \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Reméras de Niño"))
			.andExpect(jsonPath("$.slug").value("remeras-de-nino"))
			.andExpect(jsonPath("$.active").value(true))
			.andReturn()
			.getResponse();
		String categoryId = objectMapper.readTree(created.getContentAsString()).get("id").asText();
		assertThat(created.getHeader("Location")).endsWith("/" + categoryId);

		mockMvc.perform(authenticated(
				put("/api/v1/stores/tienda-a/admin/categories/{id}", categoryId),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Remeras infantiles\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Remeras infantiles"))
			.andExpect(jsonPath("$.slug").value("remeras-de-nino"));

		mockMvc.perform(authenticated(
				patch("/api/v1/stores/tienda-a/admin/categories/{id}/status", categoryId),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/categories")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].active").value(false));
		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/categories")
				.param("status", "ACTIVE")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(authenticated(
				patch("/api/v1/stores/tienda-a/admin/categories/{id}/status", categoryId),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void staffCanReadButCannotCreateRenameOrChangeStatus() throws Exception {
		insertCategory(TENANT_A_DATABASE, "Remeras", "remeras");
		AuthenticatedCookies staff = login("staff@example.com");
		String categoryId = categoryId(TENANT_A_DATABASE, "remeras");

		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/categories")
				.cookie(staff.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("Remeras"));

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Pantalones\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(authenticated(
				put("/api/v1/stores/tienda-a/admin/categories/{id}", categoryId),
				staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Otra\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(authenticated(
				patch("/api/v1/stores/tienda-a/admin/categories/{id}/status", categoryId),
				staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminCanWriteButMutationsStillRequireCsrf() throws Exception {
		AuthenticatedCookies admin = login("admin@example.com");

		mockMvc.perform(post("/api/v1/stores/tienda-a/admin/categories")
				.cookie(admin.sessionCookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Pantalones\"}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Pantalones\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void keepsCategoriesIsolatedAndIgnoresClientRoutingHints() throws Exception {
		AuthenticatedCookies owner = login("owner@example.com");

		MockHttpServletResponse created = mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories")
					.header("X-Database-Key", "tenant-b")
					.queryParam("database_key", "tenant-b"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Remeras\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse();
		String categoryId = objectMapper.readTree(created.getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/v1/stores/tienda-b/admin/categories")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get("/api/v1/stores/tienda-b/admin/categories/{id}", categoryId)
				.cookie(owner.sessionCookie()))
			.andExpect(status().isNotFound());

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-b/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Remeras\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void rejectsInvalidAndDuplicateNamesAndHidesInternalIds() throws Exception {
		AuthenticatedCookies owner = login("owner@example.com");

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Remeras\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isString())
			.andExpect(jsonPath("$.internalId").doesNotExist());

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"rémeras\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Categoría duplicada"));

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"A\"}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Solicitud inválida"))
			.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void rejectsDifferentNamesThatGenerateTheSameStableSlug() throws Exception {
		AuthenticatedCookies owner = login("owner@example.com");

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ropa de niños\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("ropa-de-ninos"));

		mockMvc.perform(authenticated(
				post("/api/v1/stores/tienda-a/admin/categories"),
				owner)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ropa-de-niños\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void databaseConstraintResolvesConcurrentDuplicateCreation() throws Exception {
		AuthenticatedCookies owner = login("owner@example.com");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> create = () -> mockMvc.perform(authenticated(
					post("/api/v1/stores/tienda-a/admin/categories"),
					owner)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Accesorios\"}"))
				.andReturn()
				.getResponse()
				.getStatus();
			var results = executor.invokeAll(List.of(create, create));
			assertThat(results)
				.extracting(Future::get)
				.containsExactlyInAnyOrder(201, 409);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void requiresAuthenticationAndReturnsNotFoundForUnknownPublicIds() throws Exception {
		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/categories"))
			.andExpect(status().isUnauthorized());

		AuthenticatedCookies owner = login("owner@example.com");
		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/categories/{id}", UUID.randomUUID())
				.cookie(owner.sessionCookie()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Categoría no encontrada"));
	}

	private AuthenticatedCookies login(String email) throws Exception {
		Cookie initialCsrf = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("XSRF-TOKEN");
		if (initialCsrf == null) {
			throw new AssertionError("Missing initial XSRF-TOKEN");
		}

		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(initialCsrf)
				.header("X-XSRF-TOKEN", initialCsrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		return new AuthenticatedCookies(
			requiredCookie(response, "CFSESSION"),
			requiredCookie(response, "XSRF-TOKEN"));
	}

	private MockHttpServletRequestBuilder authenticated(
			MockHttpServletRequestBuilder request,
			AuthenticatedCookies cookies) {
		return request
			.cookie(cookies.sessionCookie(), cookies.csrfCookie())
			.header("X-XSRF-TOKEN", cookies.csrfCookie().getValue());
	}

	private void insertUser(String email, String displayName, String role, boolean bothStores) {
		control.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, ?, 'ACTIVE')
			""", email, displayName, PASSWORD_HASH);
		control.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, ?, 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = ?
				AND tenant.slug = 'tienda-a'
			""", role, email);
		if (bothStores) {
			control.update("""
				INSERT INTO memberships (user_id, tenant_id, role, status)
				SELECT user.id, tenant.id, ?, 'ACTIVE'
				FROM platform_users user, tenants tenant
				WHERE user.email_normalized = ?
					AND tenant.slug = 'tienda-b'
				""", role, email);
		}
	}

	private static void insertCategory(
			MySQLContainer<?> database,
			String name,
			String slug) throws SQLException {
		execute(database, """
			INSERT INTO categories (public_id, name, slug, status)
			VALUES (UNHEX(REPLACE(UUID(), '-', '')), '%s', '%s', 'ACTIVE')
			""".formatted(name, slug));
	}

	private static String categoryId(MySQLContainer<?> database, String slug)
			throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				database.getJdbcUrl(),
				database.getUsername(),
				database.getPassword());
				Statement statement = connection.createStatement();
				var result = statement.executeQuery(
					"SELECT BIN_TO_UUID(public_id) FROM categories WHERE slug = '" + slug + "'")) {
			if (!result.next()) {
				throw new AssertionError("Missing category " + slug);
			}
			return result.getString(1);
		}
	}

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) {
			throw new AssertionError("Missing response cookie " + name);
		}
		return cookie;
	}

	private static void registerTenant(
			DynamicPropertyRegistry registry,
			String databaseKey,
			MySQLContainer<?> database) {
		String prefix = "app.database.tenant-connections." + databaseKey;
		registry.add(prefix + ".url", database::getJdbcUrl);
		registry.add(prefix + ".username", database::getUsername);
		registry.add(prefix + ".password", database::getPassword);
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
