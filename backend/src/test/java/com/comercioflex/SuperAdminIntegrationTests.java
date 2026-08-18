package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SuperAdminIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.10");
	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String PASSWORD_HASH =
		"{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
	private static final UUID COMPANY_ID =
		UUID.fromString("018f0000-0000-7000-8000-000000000100");

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>(MYSQL_IMAGE);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource controlDataSource;

	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", DATABASE::getUsername);
		registry.add("spring.datasource.password", DATABASE::getPassword);
		registry.add("spring.flyway.user", DATABASE::getUsername);
		registry.add("spring.flyway.password", DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "false");
		registry.add("app.database.migration-username", () -> "root");
		registry.add("app.database.migration-password", DATABASE::getPassword);
		registry.add("app.database.managed.provisioning-enabled", () -> "true");
		registry.add("app.database.managed.provisioning-url", () ->
			"jdbc:mysql://" + DATABASE.getHost() + ":" + DATABASE.getMappedPort(3306) + "/");
		registry.add("app.database.managed.provisioning-username", () -> "root");
		registry.add("app.database.managed.provisioning-password", DATABASE::getPassword);
		registry.add("app.database.managed.url-template", () ->
			"jdbc:mysql://" + DATABASE.getHost() + ":" + DATABASE.getMappedPort(3306)
				+ "/{database}");
		registry.add("app.database.managed.application-username", () -> "root");
		registry.add("app.database.managed.application-password", DATABASE::getPassword);
		registry.add("app.cors.allowed-origin", () -> "http://localhost:4200");
		registerTenant(registry, "tenant-urban");
	}

	@BeforeEach
	void seedControlData() {
		jdbcTemplate = new JdbcTemplate(controlDataSource);
		jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		jdbcTemplate.update("DELETE FROM SPRING_SESSION");
		jdbcTemplate.update("DELETE FROM platform_audit_events");
		jdbcTemplate.update("DELETE FROM memberships");
		jdbcTemplate.update("DELETE FROM tenant_infrastructure");
		jdbcTemplate.update("DELETE FROM platform_users");
		jdbcTemplate.update("DELETE FROM tenants");
		jdbcTemplate.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES (UUID_TO_BIN(?), 'urban-clothes', 'Urban Clothes', 'ACTIVE', 'tenant-urban')
			""", COMPANY_ID.toString());
		insertUser("superadmin@example.com", "Operador Plataforma", "SUPER_ADMIN");
		insertUser("owner@example.com", "Juan Pérez", "USER");
		jdbcTemplate.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT platform_user.id, tenant.id, 'OWNER', 'ACTIVE'
			FROM platform_users platform_user, tenants tenant
			WHERE platform_user.email_normalized = 'owner@example.com'
				AND tenant.slug = 'urban-clothes'
			""");
	}

	@Test
	void requiresAuthenticationAndRejectsATenantOwner() throws Exception {
		mockMvc.perform(get("/api/v1/superadmin/dashboard"))
			.andExpect(status().isUnauthorized());

		AuthenticatedCookies owner = login("owner@example.com");
		mockMvc.perform(get("/api/v1/superadmin/dashboard")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/superadmin/companies")
				.cookie(owner.sessionCookie(), owner.csrfCookie())
				.header("X-XSRF-TOKEN", owner.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/auth/session")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.platformRole").value("USER"));
	}

	@Test
	void exposesOnlySafeGlobalCompanyInformationToSuperAdmin() throws Exception {
		AuthenticatedCookies superAdmin = login("superadmin@example.com");

		mockMvc.perform(get("/api/v1/superadmin/dashboard")
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCompanies").value(1))
			.andExpect(jsonPath("$.activeCompanies").value(1));

		mockMvc.perform(get("/api/v1/superadmin/companies")
				.queryParam("q", "owner@example.com")
				.queryParam("status", "ACTIVE")
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(COMPANY_ID.toString()))
			.andExpect(jsonPath("$.items[0].slug").value("urban-clothes"))
			.andExpect(jsonPath("$.items[0].primaryAdministrator.email")
				.value("owner@example.com"))
			.andExpect(jsonPath("$.items[0].databaseKey").doesNotExist());

		mockMvc.perform(get("/api/v1/superadmin/companies/{id}", COMPANY_ID)
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.domain").doesNotExist())
			.andExpect(jsonPath("$.lastActivityAt").doesNotExist())
			.andExpect(jsonPath("$.databaseKey").doesNotExist());

		mockMvc.perform(get("/api/v1/auth/session")
				.cookie(superAdmin.sessionCookie()))
			.andExpect(jsonPath("$.user.platformRole").value("SUPER_ADMIN"));
	}

	@Test
	void exposesProvisioningCapabilityWithoutReturningCredentials() throws Exception {
		AuthenticatedCookies superAdmin = login("superadmin@example.com");

		mockMvc.perform(get("/api/v1/superadmin/provisioning-capability")
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.provider").value("MANAGED_MYSQL"))
			.andExpect(jsonPath("$.reason").doesNotExist())
			.andExpect(jsonPath("$.username").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.url").doesNotExist());
	}

	@Test
	void suspendsAndReactivatesACompanyWithCsrfAndAudit() throws Exception {
		AuthenticatedCookies superAdmin = login("superadmin@example.com");
		AuthenticatedCookies owner = login("owner@example.com");

		mockMvc.perform(post("/api/v1/superadmin/companies/{id}/suspend", COMPANY_ID)
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/superadmin/companies/{id}/suspend", COMPANY_ID)
				.cookie(superAdmin.sessionCookie(), superAdmin.csrfCookie())
				.header("X-XSRF-TOKEN", superAdmin.csrfCookie().getValue()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));

		mockMvc.perform(get("/api/v1/stores/urban-clothes/admin/probe")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isNotFound());

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM platform_audit_events WHERE action_name = 'COMPANY_SUSPENDED'",
			Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.previousStatus')) FROM platform_audit_events",
			String.class)).isEqualTo("ACTIVE");

		mockMvc.perform(post("/api/v1/superadmin/companies/{id}/activate", COMPANY_ID)
				.cookie(superAdmin.sessionCookie(), superAdmin.csrfCookie())
				.header("X-XSRF-TOKEN", superAdmin.csrfCookie().getValue()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM platform_audit_events",
			Integer.class)).isEqualTo(2);
	}

	@Test
	void exposesAndUpdatesTheCompleteCompanyRecordWithoutInfrastructureSecrets()
			throws Exception {
		AuthenticatedCookies superAdmin = login("superadmin@example.com");

		mockMvc.perform(put("/api/v1/superadmin/companies/{id}", COMPANY_ID)
				.cookie(superAdmin.sessionCookie(), superAdmin.csrfCookie())
				.header("X-XSRF-TOKEN", superAdmin.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Urban Clothes Palermo",
					  "industry": "Indumentaria",
					  "phone": "+54 11 4444-5555",
					  "domain": "urban.example.com"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Urban Clothes Palermo"))
			.andExpect(jsonPath("$.industry").value("Indumentaria"))
			.andExpect(jsonPath("$.domain").value("urban.example.com"))
			.andExpect(jsonPath("$.databaseKey").doesNotExist());

		mockMvc.perform(get("/api/v1/superadmin/companies/{id}/users", COMPANY_ID)
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].email").value("owner@example.com"))
			.andExpect(jsonPath("$[0].role").value("OWNER"))
			.andExpect(jsonPath("$[0].passwordHash").doesNotExist());

		mockMvc.perform(get("/api/v1/superadmin/companies/{id}/activity", COMPANY_ID)
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].action").value("COMPANY_UPDATED"))
			.andExpect(jsonPath("$.items[0].actorEmail").value("superadmin@example.com"))
			.andExpect(jsonPath("$.items[0].metadata").doesNotExist());

		mockMvc.perform(get("/api/v1/superadmin/companies/{id}/infrastructure", COMPANY_ID)
				.cookie(superAdmin.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isolationMode").value("DATABASE_PER_TENANT"))
			.andExpect(jsonPath("$.provisioningStatus").value("EXTERNAL"))
			.andExpect(jsonPath("$.customDomainConfigured").value(true))
			.andExpect(jsonPath("$.databaseName").doesNotExist())
			.andExpect(jsonPath("$.databaseKey").doesNotExist())
			.andExpect(jsonPath("$.failureReason").doesNotExist());

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM platform_audit_events
			WHERE action_name = 'COMPANY_UPDATED'
			""", Integer.class)).isEqualTo(1);
	}

	@Test
	void provisionsAnIsolatedTenantAndOwnerWithoutRestarting() throws Exception {
		AuthenticatedCookies superAdmin = login("superadmin@example.com");
		String initialPassword = "urban-initial-password";

		mockMvc.perform(post("/api/v1/superadmin/companies")
				.cookie(superAdmin.sessionCookie(), superAdmin.csrfCookie())
				.header("X-XSRF-TOKEN", superAdmin.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Nueva Tienda",
					  "slug": "nueva-tienda",
					  "industry": "Indumentaria",
					  "administratorEmail": "maria@example.com",
					  "administratorName": "María Dueña",
					  "administratorPhone": "+54 11 5555-5555",
					  "domain": "nueva.example.com",
					  "initialPassword": "%s",
					  "status": "ACTIVE"
					}
					""".formatted(initialPassword)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("nueva-tienda"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.industry").value("Indumentaria"))
			.andExpect(jsonPath("$.databaseKey").doesNotExist());

		String databaseName = jdbcTemplate.queryForObject("""
			SELECT infrastructure.database_name
			FROM tenant_infrastructure infrastructure
			JOIN tenants tenant ON tenant.id = infrastructure.tenant_id
			WHERE tenant.slug = 'nueva-tienda'
			""", String.class);
		assertThat(databaseName).startsWith("comercio_flex_tenant_");
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM memberships membership
			JOIN platform_users platform_user ON platform_user.id = membership.user_id
			JOIN tenants tenant ON tenant.id = membership.tenant_id
			WHERE platform_user.email_normalized = 'maria@example.com'
				AND tenant.slug = 'nueva-tienda'
				AND membership.role = 'OWNER'
				AND membership.status = 'ACTIVE'
			""", Integer.class)).isEqualTo(1);

		mockMvc.perform(get("/api/v1/stores/nueva-tienda/settings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storeName").value("Nueva Tienda"))
			.andExpect(jsonPath("$.contactEmail").value("maria@example.com"));

		AuthenticatedCookies owner = login("maria@example.com", initialPassword);
		mockMvc.perform(get("/api/v1/stores/nueva-tienda/admin/settings")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("nueva-tienda"));

		mockMvc.perform(get("/api/v1/stores/urban-clothes/admin/settings")
				.cookie(owner.sessionCookie()))
			.andExpect(status().isForbidden());

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM platform_audit_events
			WHERE action_name IN (
				'COMPANY_PROVISIONING_STARTED', 'COMPANY_PROVISIONING_COMPLETED'
			)
			""", Integer.class)).isEqualTo(2);
	}

	private void insertUser(String email, String displayName, String role) {
		jdbcTemplate.update("""
			INSERT INTO platform_users (
				public_id, email_normalized, display_name, password_hash, status, platform_role
			)
			VALUES (UUID_TO_BIN(?), ?, ?, ?, 'ACTIVE', ?)
			""", UUID.randomUUID().toString(), email, displayName, PASSWORD_HASH, role);
	}

	private AuthenticatedCookies login(String email) throws Exception {
		return login(email, PASSWORD);
	}

	private AuthenticatedCookies login(String email, String password) throws Exception {
		CsrfCookies csrf = csrf();
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrf.csrfCookie())
				.header("X-XSRF-TOKEN", csrf.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}
					""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		return new AuthenticatedCookies(
			requiredCookie(response, "CFSESSION"),
			requiredCookie(response, "XSRF-TOKEN"));
	}

	private CsrfCookies csrf() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
		return new CsrfCookies(requiredCookie(response, "XSRF-TOKEN"));
	}

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) throw new AssertionError("Missing response cookie " + name);
		return cookie;
	}

	private static void registerTenant(DynamicPropertyRegistry registry, String databaseKey) {
		String prefix = "app.database.tenant-connections." + databaseKey;
		registry.add(prefix + ".url", DATABASE::getJdbcUrl);
		registry.add(prefix + ".username", DATABASE::getUsername);
		registry.add(prefix + ".password", DATABASE::getPassword);
	}

	private record CsrfCookies(Cookie csrfCookie) {
	}

	private record AuthenticatedCookies(Cookie sessionCookie, Cookie csrfCookie) {
	}
}
