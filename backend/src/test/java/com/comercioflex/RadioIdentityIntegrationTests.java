package com.comercioflex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.comercioflex.identity.application.PublicIdentityService;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.notification.application.TransactionalEmailSender;
import com.comercioflex.tenant.application.TenantContext;
import jakarta.servlet.http.Cookie;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class RadioIdentityIntegrationTests {

	@Container static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.4.10");
	@Autowired MockMvc mvc;
	@Autowired @Qualifier("controlJdbcTemplate") JdbcTemplate jdbc;
	@Autowired PasswordEncoder passwords;
	@Autowired TenantContext tenantContext;
	@MockitoBean TransactionalEmailSender sender;
	private final BlockingQueue<TransactionalEmail> sent = new LinkedBlockingQueue<>();
	private static final String PASSWORD = "initial-radio-password";
	private String email;
	private String address;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", DB::getJdbcUrl);
		registry.add("spring.datasource.username", DB::getUsername);
		registry.add("spring.datasource.password", DB::getPassword);
		registry.add("spring.flyway.user", DB::getUsername);
		registry.add("spring.flyway.password", DB::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "false");
		registry.add("app.email.enabled", () -> "true");
		registry.add("app.email.host", () -> "localhost");
		registry.add("app.identity.public-base-uri", () -> "https://platform.example");
		for (String key : new String[] {"tenant-a", "tenant-b", "tenant-shop"}) {
			String prefix = "app.database.tenant-connections." + key;
			registry.add(prefix + ".url", DB::getJdbcUrl);
			registry.add(prefix + ".username", DB::getUsername);
			registry.add(prefix + ".password", DB::getPassword);
		}
	}

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
		jdbc.update("DELETE FROM SPRING_SESSION");
		jdbc.update("DELETE FROM memberships");
		jdbc.update("DELETE FROM platform_users");
		jdbc.update("DELETE FROM tenants");
		jdbc.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key, tenant_type) VALUES
			(UUID_TO_BIN(UUID()), 'radio-a', 'Radio A', 'ACTIVE', 'tenant-a', 'RADIO'),
			(UUID_TO_BIN(UUID()), 'radio-b', 'Radio B', 'ACTIVE', 'tenant-b', 'RADIO'),
			(UUID_TO_BIN(UUID()), 'shop', 'Shop', 'ACTIVE', 'tenant-shop', 'ECOMMERCE')
			""");
		email = UUID.randomUUID() + "@example.com";
		address = UUID.randomUUID().toString();
		sent.clear();
		doAnswer(invocation -> { sent.add(invocation.getArgument(0)); return null; }).when(sender).send(any());
	}

	@Test
	void registersAGlobalIdentityWithoutAdministrativeMembershipOrSecretExposure() throws Exception {
		register(email.toUpperCase()).andExpect(status().isAccepted());
		String hash = jdbc.queryForObject("SELECT password_hash FROM platform_users WHERE email_normalized = ?", String.class, email);
		assertThat(hash).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
		assertThat(passwords.matches(PASSWORD, hash)).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memberships", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT platform_role FROM platform_users", String.class)).isEqualTo("USER");
		var session = login(email, PASSWORD);
		mvc.perform(get("/api/v1/auth/session").cookie(session.session()))
			.andExpect(jsonPath("$.memberships").isEmpty()).andExpect(jsonPath("$.user.platformRole").value("USER"));
		mvc.perform(get(base("radio-a") + "/me/profile").cookie(session.session()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Ana"))
			.andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.role").doesNotExist());
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void duplicateRegistrationHasTheSameResponseAndCannotOverwriteGlobalCredentials() throws Exception {
		String original = register(email).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		String changed = registration(email).replace(PASSWORD, "attacker-new-password");
		write(base("radio-b") + "/member-registration", changed, null)
			.andExpect(status().isAccepted()).andExpect(content().json(original));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_users", Integer.class)).isEqualTo(1);
		login(email, PASSWORD);
	}

	@Test
	void registrationIsRadioOnlyAndValidatesTenantAndFields() throws Exception {
		write(base("shop") + "/member-registration", registration(email), null).andExpect(status().isNotFound());
		write(base("unknown") + "/member-registration", registration(email), null).andExpect(status().isNotFound());
		jdbc.update("UPDATE tenants SET status = 'INACTIVE' WHERE slug = 'radio-b'");
		write(base("radio-b") + "/member-registration", registration(email), null).andExpect(status().isNotFound());
		write(base("radio-a") + "/member-registration", registration(email).replace("Ana", " "), null).andExpect(status().isBadRequest());
		write(base("radio-a") + "/member-registration", registration("not-email"), null).andExpect(status().isBadRequest());
		write(base("radio-a") + "/member-registration", registration(email).replace(PASSWORD, "short"), null).andExpect(status().isBadRequest());
		write(base("radio-a") + "/member-registration", registration(email).replace(PASSWORD, "á".repeat(40)), null).andExpect(status().isBadRequest());
		write(base("radio-a") + "/member-registration", registration(email).replace("{", "{\"role\":\"OWNER\","), null)
			.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_users", Integer.class)).isZero();
	}

	@Test
	void meAlwaysUsesSessionIdentityAcrossRadiosAndRejectsPrivilegedFields() throws Exception {
		register(email);
		String other = UUID.randomUUID() + "@example.com";
		write(base("radio-b") + "/member-registration", registration(other).replace("Ana", "Bea"), null).andExpect(status().isAccepted());
		var session = login(email, PASSWORD);
		long otherId = jdbc.queryForObject("SELECT id FROM platform_users WHERE email_normalized = ?", Long.class, other);
		for (String slug : new String[] {"radio-a", "radio-b"}) {
			mvc.perform(get(base(slug) + "/me/profile").cookie(session.session())
				.param("userId", Long.toString(otherId)).header("X-Database-Key", "tenant-b"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.firstName").value("Ana"));
		}
		for (String field : new String[] {"userId", "role", "permissions", "databaseKey", "email"}) {
			putProfile(session, "{\"firstName\":\"Eva\",\"lastName\":\"Diaz\",\"" + field + "\":\"forged\"}")
				.andExpect(status().isBadRequest());
		}
		putProfile(session, "{\"firstName\":\"Eva\",\"lastName\":\"Diaz\",\"phone\":\"123\"}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Eva"));
		assertThat(jdbc.queryForObject("SELECT first_name FROM platform_users WHERE id = ?", String.class, otherId)).isEqualTo("Bea");
		mvc.perform(get(base("radio-a") + "/me/profile/" + otherId).cookie(session.session())).andExpect(status().isNotFound());
		mvc.perform(get(base("shop") + "/me/profile").cookie(session.session())).andExpect(status().isNotFound());
		assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void publicUserCannotAccessAnyAdministrativeArea() throws Exception {
		register(email);
		var session = login(email, PASSWORD);
		for (String path : new String[] {"/api/v1/superadmin/companies", base("radio-a") + "/admin/settings",
				base("radio-b") + "/admin/settings", base("shop") + "/admin/products"}) {
			mvc.perform(get(path).cookie(session.session())).andExpect(status().isForbidden());
		}
	}

	@Test
	void recoveryIsNonEnumeratingHashedTenantBoundAndSingleUseAndRevokesSessions() throws Exception {
		register(email);
		var oldSession = login(email, PASSWORD);
		String response = write(base("radio-a") + "/account/password/forgot", "{\"email\":\"" + email + "\"}", null)
			.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		String token = deliveredToken();
		assertThat(response).doesNotContain(token).doesNotContain(email);
		write(base("radio-a") + "/account/password/forgot", "{\"email\":\"absent-" + email + "\"}", null)
			.andExpect(status().isAccepted()).andExpect(content().json(response));
		assertThat(jdbc.queryForObject("SELECT token_hash FROM identity_password_resets", byte[].class))
			.isEqualTo(PublicIdentityService.hash(token));
		String body = "{\"token\":\"" + token + "\",\"password\":\"new-radio-password\"}";
		write(base("radio-b") + "/account/password/reset", body, null).andExpect(status().isBadRequest());
		write(base("radio-a") + "/account/password/reset", body, null).andExpect(status().isNoContent());
		write(base("radio-a") + "/account/password/reset", body, null).andExpect(status().isBadRequest());
		write("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null)
			.andExpect(status().isUnauthorized());
		login(email, "new-radio-password");
		mvc.perform(get(base("radio-a") + "/me/profile").cookie(oldSession.session())).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_password_resets", Integer.class)).isZero();
	}

	@Test
	void recoveryExpiresAndANewRequestInvalidatesTheEarlierToken() throws Exception {
		register(email);
		String first = forgot();
		String second = forgot();
		write(base("radio-a") + "/account/password/reset", resetBody(first), null).andExpect(status().isBadRequest());
		jdbc.update("UPDATE identity_password_resets SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)");
		write(base("radio-a") + "/account/password/reset", resetBody(second), null).andExpect(status().isBadRequest());
		login(email, PASSWORD);
	}

	@Test
	void everyPublicIdentityMutationAndLogoutRequiresCsrf() throws Exception {
		for (String path : new String[] {base("radio-a") + "/member-registration", base("radio-a") + "/account/password/forgot",
				base("radio-a") + "/account/password/reset", "/api/v1/auth/login", "/api/v1/auth/logout"}) {
			mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		}
		register(email);
		var session = login(email, PASSWORD);
		mvc.perform(put(base("radio-a") + "/me/profile").cookie(session.session()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
		write("/api/v1/auth/logout", "{}", session).andExpect(status().isOk());
		mvc.perform(get(base("radio-a") + "/me/profile").cookie(session.session())).andExpect(status().isUnauthorized());
	}

	@Test
	void staleCredentialSessionsAreRejectedEvenIfStillPresentInTheSessionStore() throws Exception {
		register(email);
		var session = login(email, PASSWORD);
		jdbc.update("UPDATE platform_users SET password_hash = ? WHERE email_normalized = ?", passwords.encode("changed-password"), email);
		mvc.perform(get(base("radio-a") + "/me/profile").cookie(session.session())).andExpect(status().isUnauthorized());
	}

	@Test
	void validationDoesNotReturnOrLogRejectedSecrets(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
		String rejectedPassword = "s3cr3t!";
		String response = write(base("radio-a") + "/member-registration", registration(email).replace(PASSWORD, rejectedPassword), null)
			.andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain(rejectedPassword);
		String rejectedToken = "invalid-private-token";
		write(base("radio-a") + "/account/password/reset", resetBody(rejectedToken), null).andExpect(status().isBadRequest());
		assertThat(output.getAll()).doesNotContain(rejectedPassword, rejectedToken);
	}

	@Test
	void concurrentResetRequestsConsumeTheTokenExactlyOnce() throws Exception {
		register(email).andExpect(status().isAccepted());
		String token = forgot();
		var gate = new java.util.concurrent.CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			java.util.concurrent.Callable<Integer> reset = () -> {
				gate.await();
				return write(base("radio-a") + "/account/password/reset", resetBody(token), null)
					.andReturn().getResponse().getStatus();
			};
			var first = executor.submit(reset);
			var second = executor.submit(reset);
			gate.countDown();
			assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(204, 400);
		}
	}

	@Test
	void recoveryCannotChangeThePasswordOfADisabledAccount() throws Exception {
		register(email).andExpect(status().isAccepted());
		String token = forgot();
		jdbc.update("UPDATE platform_users SET status = 'DISABLED' WHERE email_normalized = ?", email);
		write(base("radio-a") + "/account/password/reset", resetBody(token), null).andExpect(status().isBadRequest());
		assertThat(passwords.matches(PASSWORD, jdbc.queryForObject("SELECT password_hash FROM platform_users WHERE email_normalized = ?", String.class, email))).isTrue();
	}

	@Test
	void forgotIsRateLimitedEvenForNonexistentAccounts() throws Exception {
		String body = "{\"email\":\"" + email + "\"}";
		for (int i = 0; i < 10; i++) write(base("radio-a") + "/account/password/forgot", body, null).andExpect(status().isAccepted());
		write(base("radio-a") + "/account/password/forgot", body, null).andExpect(status().isTooManyRequests());
	}

	private ResultActions register(String email) throws Exception { return write(base("radio-a") + "/member-registration", registration(email), null); }
	private String registration(String email) {
		return "{\"firstName\":\"Ana\",\"lastName\":\"Diaz\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
	}
	private String resetBody(String token) { return "{\"token\":\"" + token + "\",\"password\":\"new-radio-password\"}"; }
	private String base(String slug) { return "/api/v1/stores/" + slug; }
	private String forgot() throws Exception {
		write(base("radio-a") + "/account/password/forgot", "{\"email\":\"" + email + "\"}", null).andExpect(status().isAccepted());
		return deliveredToken();
	}
	private String deliveredToken() throws Exception {
		var message = sent.poll(5, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.textBody()).contains("https://platform.example/radio-a/nueva-contrasena#token=");
		return message.textBody().split("#token=")[1].substring(0, 43);
	}
	private ResultActions write(String path, String body, Session session) throws Exception {
		Cookie csrf = session == null ? csrf() : session.csrf();
		Cookie[] cookies = session == null ? new Cookie[] {csrf} : new Cookie[] {session.session(), csrf};
		return mvc.perform(post(path).with(r -> { r.setRemoteAddr(address); return r; })
			.cookie(cookies).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
	}
	private ResultActions putProfile(Session session, String body) throws Exception {
		return mvc.perform(put(base("radio-a") + "/me/profile").cookie(session.session(), session.csrf())
			.header("X-XSRF-TOKEN", session.csrf().getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
	}
	private Cookie csrf() throws Exception {
		return mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
	}
	private Session login(String email, String password) throws Exception {
		MockHttpServletResponse response = write("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null)
			.andExpect(status().isOk()).andReturn().getResponse();
		return new Session(response.getCookie("CFSESSION"), response.getCookie("XSRF-TOKEN"));
	}
	private record Session(Cookie session, Cookie csrf) {}
}
