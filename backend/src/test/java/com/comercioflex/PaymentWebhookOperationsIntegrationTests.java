package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.comercioflex.payment.application.CheckoutControlRepository;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookOperationsIntegrationTests {

	private static final String PASSWORD = "correct-horse-battery-staple";
	private static final String PASSWORD_HASH =
		"{bcrypt}" + new BCryptPasswordEncoder(4).encode(PASSWORD);
	private static final UUID DEAD_A = UUID.fromString("018f0000-0000-7000-8000-000000000101");
	private static final UUID PROCESSED_A = UUID.fromString("018f0000-0000-7000-8000-000000000102");
	private static final UUID DEAD_PRODUCTION = UUID.fromString("018f0000-0000-7000-8000-000000000103");
	private static final UUID DEAD_B = UUID.fromString("018f0000-0000-7000-8000-000000000104");
	private static final UUID PROCESSING_A = UUID.fromString("018f0000-0000-7000-8000-000000000105");
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T12:00:00Z");

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>(
		DockerImageName.parse("mysql:8.4.10"));

	@Autowired MockMvc mockMvc;
	@Autowired DataSource controlDataSource;
	@Autowired CheckoutControlRepository repository;
	@Autowired ObjectMapper objectMapper;
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", DATABASE::getUsername);
		registry.add("spring.datasource.password", DATABASE::getPassword);
		registry.add("spring.flyway.user", DATABASE::getUsername);
		registry.add("spring.flyway.password", DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "false");
		registerTenant(registry, "tenant-a");
		registerTenant(registry, "tenant-b");
	}

	@BeforeEach
	void seed() {
		jdbc = new JdbcTemplate(controlDataSource);
		jdbc.update("DELETE FROM payment_webhook_retry_audit");
		jdbc.update("DELETE FROM payment_webhook_events");
		jdbc.update("DELETE FROM payment_webhook_routes");
		jdbc.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		jdbc.update("DELETE FROM SPRING_SESSION");
		jdbc.update("DELETE FROM memberships");
		jdbc.update("DELETE FROM platform_users");
		jdbc.update("DELETE FROM tenants");
		jdbc.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES (UUID_TO_BIN(UUID()), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
			       (UUID_TO_BIN(UUID()), 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		insertUser("owner@example.com", "OWNER");
		insertUser("admin@example.com", "ADMIN");
		insertUser("staff@example.com", "STAFF");
		jdbc.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, 'OWNER', 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = 'owner@example.com' AND tenant.slug = 'tienda-b'
			""");
		insertEvent(DEAD_A, "tienda-a", "TEST", "DEAD", 8, "PAYMENT_LOOKUP_FAILED");
		insertEvent(PROCESSED_A, "tienda-a", "TEST", "PROCESSED", 1, null);
		insertEvent(DEAD_PRODUCTION, "tienda-a", "PRODUCTION", "DEAD", 8, "PAYMENT_LOOKUP_FAILED");
		insertEvent(DEAD_B, "tienda-b", "TEST", "DEAD", 8, "PAYMENT_LOOKUP_FAILED");
		insertEvent(PROCESSING_A, "tienda-a", "TEST", "PROCESSING", 3, null);
	}

	@Test
	void securesIsolatesAndIdempotentlyAuditsManualRecovery() throws Exception {
		String path = "/api/v1/stores/tienda-a/admin/payment-webhooks";
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(path).cookie(login("admin@example.com").session()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get(path).cookie(login("staff@example.com").session()))
			.andExpect(status().isForbidden());

		Authenticated owner = login("owner@example.com");
		mockMvc.perform(get(path).cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].eventId").value(DEAD_A.toString()))
			.andExpect(jsonPath("$[0].status").value("DEAD"))
			.andExpect(jsonPath("$[0].safeErrorCode").value("PAYMENT_LOOKUP_FAILED"))
			.andExpect(jsonPath("$[0].occurredAt").value(OCCURRED_AT.toString()))
			.andExpect(jsonPath("$[0].providerResourceId").doesNotExist());

		mockMvc.perform(get("/api/v1/stores/tienda-b/admin/payment-webhooks")
				.cookie(owner.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].eventId").value(DEAD_B.toString()));
		mockMvc.perform(post("/api/v1/stores/tienda-b/admin/payment-webhooks/{id}/retry", DEAD_A)
				.cookie(owner.session(), owner.csrf())
				.header("X-XSRF-TOKEN", owner.csrf().getValue()))
			.andExpect(status().isNotFound());

		String retryPath = path + "/" + DEAD_A + "/retry";
		mockMvc.perform(post(retryPath).cookie(owner.session()))
			.andExpect(status().isForbidden());
		String firstBody = mockMvc.perform(post(retryPath)
				.cookie(owner.session(), owner.csrf())
				.header("X-XSRF-TOKEN", owner.csrf().getValue()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RETRY_SCHEDULED"))
			.andReturn().getResponse().getContentAsString();
		String scheduledAt = objectMapper.readTree(firstBody).path("scheduledAt").asText();
		mockMvc.perform(post(retryPath)
				.cookie(owner.session(), owner.csrf())
				.header("X-XSRF-TOKEN", owner.csrf().getValue()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RETRY_SCHEDULED"))
			.andExpect(jsonPath("$.scheduledAt").value(scheduledAt));

		assertThat(jdbc.queryForObject(
			"SELECT COUNT(*) FROM payment_webhook_retry_audit", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
			SELECT previous_attempt_count FROM payment_webhook_retry_audit
			""", Integer.class)).isEqualTo(8);
		assertThat(jdbc.queryForObject("""
			SELECT CONCAT(status, ':', attempt_count) FROM payment_webhook_events
			WHERE public_id = UUID_TO_BIN(?)
			""", String.class, DEAD_A.toString())).isEqualTo("RETRY:0");

		mockMvc.perform(post(path + "/" + PROCESSED_A + "/retry")
				.cookie(owner.session(), owner.csrf())
				.header("X-XSRF-TOKEN", owner.csrf().getValue()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("WEBHOOK_ALREADY_PROCESSED"));
		assertThat(jdbc.queryForObject("""
			SELECT status FROM payment_webhook_events WHERE public_id = UUID_TO_BIN(?)
			""", String.class, PROCESSED_A.toString())).isEqualTo("PROCESSED");

		Long processingId = jdbc.queryForObject("""
			SELECT id FROM payment_webhook_events WHERE public_id = UUID_TO_BIN(?)
			""", Long.class, PROCESSING_A.toString());
		assertThat(repository.markProcessed(processingId, 2, Instant.now())).isFalse();
		assertThat(repository.markFailed(
			processingId, 2, true, "STALE_WORKER", Instant.now())).isFalse();
		assertThat(jdbc.queryForObject("""
			SELECT CONCAT(status, ':', attempt_count, ':', IF(leased_until IS NULL, 'none', 'leased'))
			FROM payment_webhook_events WHERE id = ?
			""", String.class, processingId)).isEqualTo("PROCESSING:3:leased");
	}

	private void insertUser(String email, String role) {
		jdbc.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (UUID_TO_BIN(UUID()), ?, ?, ?, 'ACTIVE')
			""", email, role, PASSWORD_HASH);
		jdbc.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			SELECT user.id, tenant.id, ?, 'ACTIVE'
			FROM platform_users user, tenants tenant
			WHERE user.email_normalized = ? AND tenant.slug = 'tienda-a'
			""", role, email);
	}

	private void insertEvent(
			UUID eventId, String tenantSlug, String environment,
			String status, int attempts, String errorCode) {
		UUID routeId = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO payment_webhook_routes (
				public_id, route_token_hash, tenant_id, environment,
				payment_intent_public_id, expected_seller_account_id,
				provider_preference_id, status, expires_at)
			SELECT UUID_TO_BIN(?), UNHEX(SHA2(?, 256)), tenant.id, ?,
				UUID_TO_BIN(UUID()), 'seller', 'preference', 'ACTIVE', ?
			FROM tenants tenant WHERE tenant.slug = ?
			""", routeId.toString(), routeId.toString(), environment,
			java.sql.Timestamp.from(Instant.now().plusSeconds(3600)), tenantSlug);
		jdbc.update("""
			INSERT INTO payment_webhook_events (
				public_id, route_id, provider, environment, notification_id,
				request_id, event_type, action_name, provider_resource_id,
				provider_user_id, live_mode, payload_hash, status, attempt_count,
				available_at, leased_until, processed_at, last_error_code,
				created_at, updated_at)
			SELECT UUID_TO_BIN(?), route.id, 'MERCADO_PAGO', ?, ?, ?, 'payment',
				'payment.updated', ?, 'seller', ?, UNHEX(SHA2(?, 256)), ?, ?, ?, ?, ?, ?, ?, ?
			FROM payment_webhook_routes route WHERE route.public_id = UUID_TO_BIN(?)
			""", eventId.toString(), environment, "notification-" + eventId,
			"request-" + eventId, "resource-" + eventId,
			"PRODUCTION".equals(environment), eventId.toString(), status, attempts,
			java.sql.Timestamp.from(Instant.now()),
			"PROCESSING".equals(status) ? java.sql.Timestamp.from(Instant.now().plusSeconds(60)) : null,
			"PROCESSED".equals(status) ? java.sql.Timestamp.from(Instant.now()) : null,
			errorCode, java.sql.Timestamp.from(OCCURRED_AT),
			java.sql.Timestamp.from(OCCURRED_AT), routeId.toString());
	}

	private Authenticated login(String email) throws Exception {
		MockHttpServletResponse csrfResponse = mockMvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isOk()).andReturn().getResponse();
		Cookie csrf = requiredCookie(csrfResponse, "XSRF-TOKEN");
		MockHttpServletResponse loginResponse = mockMvc.perform(post("/api/v1/auth/login")
				.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk()).andReturn().getResponse();
		return new Authenticated(requiredCookie(loginResponse, "CFSESSION"),
			requiredCookie(loginResponse, "XSRF-TOKEN"));
	}

	private static Cookie requiredCookie(MockHttpServletResponse response, String name) {
		Cookie cookie = response.getCookie(name);
		if (cookie == null) throw new AssertionError("Missing cookie " + name);
		return cookie;
	}

	private static void registerTenant(DynamicPropertyRegistry registry, String key) {
		String prefix = "app.database.tenant-connections." + key;
		registry.add(prefix + ".url", DATABASE::getJdbcUrl);
		registry.add(prefix + ".username", DATABASE::getUsername);
		registry.add(prefix + ".password", DATABASE::getPassword);
	}

	private record Authenticated(Cookie session, Cookie csrf) {
	}
}
