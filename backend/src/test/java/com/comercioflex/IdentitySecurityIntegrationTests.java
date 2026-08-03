package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class IdentitySecurityIntegrationTests {

	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.10");
	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String PASSWORD_HASH =
		"{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);

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
		registry.add("app.cors.allowed-origin",
			() -> "http://localhost:4200,https://demo.example");
		registerTenant(registry, "tenant-a");
		registerTenant(registry, "tenant-b");
	}

	@BeforeEach
	void seedIdentity() {
		jdbcTemplate = new JdbcTemplate(controlDataSource);
		jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		jdbcTemplate.update("DELETE FROM SPRING_SESSION");
		jdbcTemplate.update("DELETE FROM memberships");
		jdbcTemplate.update("DELETE FROM platform_users");
		jdbcTemplate.update("DELETE FROM tenants");
		jdbcTemplate.update("""
			INSERT INTO tenants
				(public_id, slug, display_name, status, database_key)
			VALUES
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
				(UNHEX(REPLACE(UUID(), '-', '')), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		jdbcTemplate.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (?, 'owner@example.com', 'Owner Demo', ?, 'ACTIVE')
			""", uuidBytes(UUID.fromString("018f0000-0000-7000-8000-000000000001")), PASSWORD_HASH);
		jdbcTemplate.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, 'OWNER', 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = 'owner@example.com'
				AND tenant.slug = 'tienda-a'
			""");
	}

	@Test
	void exposesAnAnonymousSessionWithoutCreatingAFalseIdentity() throws Exception {
		mockMvc.perform(get("/api/v1/auth/session"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(false))
			.andExpect(jsonPath("$.user").doesNotExist())
			.andExpect(jsonPath("$.memberships").isEmpty());
	}

	@Test
	void requiresCsrfForLoginAndReturnsAnAngularCompatibleAuthenticatedSession() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(PASSWORD)))
			.andExpect(status().isForbidden());

		AuthenticatedCookies authenticated = login(PASSWORD);

		mockMvc.perform(get("/api/v1/auth/session")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.user.id")
				.value("018f0000-0000-7000-8000-000000000001"))
			.andExpect(jsonPath("$.user.email").value("owner@example.com"))
			.andExpect(jsonPath("$.memberships[0].storeSlug").value("tienda-a"))
			.andExpect(jsonPath("$.memberships[0].storeName").value("Tienda A"))
			.andExpect(jsonPath("$.memberships[0].role").value("OWNER"));
	}

	@Test
	void rejectsInvalidCredentialsWithTheSamePublicProblem() throws Exception {
		CsrfCookies csrf = csrf();

		mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrf.cookies())
				.header("X-XSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("wrong-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.title").value("Credenciales inválidas"))
			.andExpect(jsonPath("$.detail").value("El correo o la contraseña no son válidos."));
	}

	@Test
	void invalidatesTheSessionOnLogout() throws Exception {
		AuthenticatedCookies authenticated = login(PASSWORD);

		mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(authenticated.sessionCookie(), authenticated.csrfCookie())
				.header("X-XSRF-TOKEN", authenticated.csrfCookie().getValue()))
			.andExpect(status().isOk())
			.andExpect(cookie().maxAge("CFSESSION", 0));

		mockMvc.perform(get("/api/v1/auth/session")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(false));
	}

	@Test
	void checksMembershipForTheCurrentStoreBeforeOpeningItsDatabase() throws Exception {
		AuthenticatedCookies authenticated = login(PASSWORD);

		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/probe")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/stores/tienda-b/admin/probe")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.title").value("Acceso denegado"));
	}

	@Test
	void deniesAdministrativeAccessWhenTheUserIsDisabledAfterLogin() throws Exception {
		AuthenticatedCookies authenticated = login(PASSWORD);
		jdbcTemplate.update(
			"UPDATE platform_users SET status = 'DISABLED' WHERE email_normalized = ?",
			"owner@example.com");

		mockMvc.perform(get("/api/v1/auth/session")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.memberships").isEmpty());

		mockMvc.perform(get("/api/v1/stores/tienda-a/admin/probe")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.title").value("Acceso denegado"));
	}

	@Test
	void returnsEveryActiveMembershipWithoutTurningRolesIntoGlobalAuthorities() throws Exception {
		jdbcTemplate.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, 'STAFF', 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = 'owner@example.com'
				AND tenant.slug = 'tienda-b'
			""");

		AuthenticatedCookies authenticated = login(PASSWORD);

		mockMvc.perform(get("/api/v1/auth/session")
				.cookie(authenticated.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberships.length()").value(2))
			.andExpect(jsonPath("$.memberships[0].storeSlug").value("tienda-a"))
			.andExpect(jsonPath("$.memberships[0].role").value("OWNER"))
			.andExpect(jsonPath("$.memberships[1].storeSlug").value("tienda-b"))
			.andExpect(jsonPath("$.memberships[1].role").value("STAFF"));
	}

	@Test
	void persistsTheAuthenticatedSessionInTheControlDatabase() throws Exception {
		login(PASSWORD);

		Integer storedSessions = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM SPRING_SESSION",
			Integer.class);
		assertThat(storedSessions).isEqualTo(1);
	}

	@Test
	void allowsOnlyTheConfiguredCorsOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
				.header("Origin", "http://localhost:4200")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

		mockMvc.perform(options("/api/v1/auth/login")
				.header("Origin", "https://demo.example")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://demo.example"));

		mockMvc.perform(options("/api/v1/auth/login")
				.header("Origin", "https://attacker.example")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void rateLimitsRepeatedFailuresWithoutRevealingWhetherTheAccountExists() throws Exception {
		for (int attempt = 0; attempt < 5; attempt++) {
			CsrfCookies csrf = csrf();
			mockMvc.perform(post("/api/v1/auth/login")
					.cookie(csrf.cookies())
					.header("X-XSRF-TOKEN", csrf.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginBody("limited@example.com", "wrong-password")))
				.andExpect(status().isUnauthorized());
		}

		CsrfCookies csrf = csrf();
		mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrf.cookies())
				.header("X-XSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("limited@example.com", "wrong-password")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.title").value("Demasiados intentos"));
	}

	private AuthenticatedCookies login(String password) throws Exception {
		CsrfCookies csrf = csrf();
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrf.cookies())
				.header("X-XSRF-TOKEN", csrf.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(password)))
			.andExpect(status().isOk())
			.andExpect(cookie().httpOnly("CFSESSION", true))
			.andReturn()
			.getResponse();

		return new AuthenticatedCookies(
			requiredCookie(response, "CFSESSION"),
			requiredCookie(response, "XSRF-TOKEN"));
	}

	private CsrfCookies csrf() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
			.andReturn()
			.getResponse();
		Cookie csrfCookie = requiredCookie(response, "XSRF-TOKEN");
		return new CsrfCookies(csrfCookie.getValue(), csrfCookie);
	}

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) {
			throw new AssertionError("Missing response cookie " + name);
		}
		return cookie;
	}

	private static String loginBody(String password) {
		return loginBody(" Owner@Example.COM ", password);
	}

	private static String loginBody(String email, String password) {
		return """
			{"email":"%s","password":"%s"}
			""".formatted(email, password);
	}

	private static void registerTenant(DynamicPropertyRegistry registry, String databaseKey) {
		String prefix = "app.database.tenant-connections." + databaseKey;
		registry.add(prefix + ".url", DATABASE::getJdbcUrl);
		registry.add(prefix + ".username", DATABASE::getUsername);
		registry.add(prefix + ".password", DATABASE::getPassword);
	}

	private static byte[] uuidBytes(UUID uuid) {
		byte[] bytes = new byte[16];
		long most = uuid.getMostSignificantBits();
		long least = uuid.getLeastSignificantBits();
		for (int index = 0; index < 8; index++) {
			bytes[index] = (byte) (most >>> (8 * (7 - index)));
			bytes[index + 8] = (byte) (least >>> (8 * (7 - index)));
		}
		return bytes;
	}

	private record CsrfCookies(String token, Cookie csrfCookie) {
		Cookie[] cookies() {
			return new Cookie[] {csrfCookie};
		}
	}

	private record AuthenticatedCookies(Cookie sessionCookie, Cookie csrfCookie) {
	}
}
