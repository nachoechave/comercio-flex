package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.UserCredentials;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Testcontainers
@SpringBootTest
class MerchantPaymentConnectionOAuthIntegrationTests {

	private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
	private static final UUID TENANT_PUBLIC_ID =
		UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private static final UUID USER_PUBLIC_ID =
		UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
	private static final UUID ATTEMPT_PUBLIC_ID =
		UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
	private static final String STATE = "oauth-state-with-long-scopes";

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>(
		DockerImageName.parse("mysql:8.4.10"));

	@Autowired MerchantOAuthRepository repository;
	@Autowired DataSource controlDataSource;
	@Autowired @Qualifier("controlTransactionTemplate") TransactionTemplate transactions;

	private JdbcTemplate jdbc;
	private long tenantId;
	private long userId;

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
		registry.add("spring.datasource.username", DATABASE::getUsername);
		registry.add("spring.datasource.password", DATABASE::getPassword);
		registry.add("spring.flyway.user", DATABASE::getUsername);
		registry.add("spring.flyway.password", DATABASE::getPassword);
		registry.add("app.database.tenant-migration-enabled", () -> "false");
	}

	@BeforeEach
	void seed() {
		jdbc = new JdbcTemplate(controlDataSource);
		jdbc.update("DELETE FROM merchant_payment_connection_events");
		jdbc.update("DELETE FROM merchant_payment_connections");
		jdbc.update("DELETE FROM payment_oauth_attempts");
		jdbc.update("DELETE FROM memberships");
		jdbc.update("DELETE FROM platform_users");
		jdbc.update("DELETE FROM tenants");
		jdbc.update("""
			INSERT INTO tenants
				(public_id, slug, display_name, status, database_key)
			VALUES (UUID_TO_BIN(?), 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a')
			""", TENANT_PUBLIC_ID.toString());
		jdbc.update("""
			INSERT INTO platform_users
				(public_id, email_normalized, display_name, password_hash, status)
			VALUES (UUID_TO_BIN(?), 'owner-a@example.test', 'Owner A', '{noop}unused', 'ACTIVE')
			""", USER_PUBLIC_ID.toString());
		tenantId = jdbc.queryForObject(
			"SELECT id FROM tenants WHERE slug = 'tienda-a'", Long.class);
		userId = jdbc.queryForObject(
			"SELECT id FROM platform_users WHERE email_normalized = 'owner-a@example.test'",
			Long.class);
		jdbc.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			VALUES (?, ?, 'OWNER', 'ACTIVE')
			""", userId, tenantId);
	}

	@Test
	void completesProductionOAuthWithLongScopesWithoutTruncation() {
		Set<String> scopes = scopesWithNormalizedLength(784);
		String normalizedScopes = normalize(scopes);
		MerchantOAuthClient client = mock(MerchantOAuthClient.class);
		CredentialCipher cipher = new PlainTestCipher();
		when(client.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		when(client.exchange("authorization-code", "pkce-verifier"))
			.thenReturn(new OAuthTokenResponse(
				"access-token", "refresh-token", "Bearer", Duration.ofHours(1),
				scopes, "seller-a", true));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("seller-a", "SELLER_A"));

		transactions.executeWithoutResult(status -> repository.insertAttempt(
			ATTEMPT_PUBLIC_ID, tenantId, userId, USER_PUBLIC_ID,
			PaymentEnvironment.PRODUCTION, sha256(STATE),
			cipher.encrypt("pkce-verifier", encryptionContext("pkce_verifier")),
			NOW.plusSeconds(600)));

		MerchantPaymentConnectionService service = new MerchantPaymentConnectionService(
			repository, client, cipher, properties(), transactions,
			new SecureRandom(new byte[] {1, 2, 3}), Clock.fixed(NOW, ZoneOffset.UTC));

		OAuthCallbackResult result = service.complete(
			STATE, "authorization-code", null, principal());

		assertThat(result).isEqualTo(new OAuthCallbackResult("tienda-a", "connected"));
		Map<String, Object> connection = jdbc.queryForMap("""
			SELECT status, granted_scopes
			FROM merchant_payment_connections
			WHERE tenant_id = ? AND environment = 'PRODUCTION'
			""", tenantId);
		assertThat(connection.get("status")).isEqualTo("CONNECTED");
		assertThat(connection.get("granted_scopes")).isEqualTo(normalizedScopes);
		assertThat(normalizedScopes).hasSize(784);
		Map<String, Object> attempt = jdbc.queryForMap("""
			SELECT status, failure_code
			FROM payment_oauth_attempts
			WHERE public_id = UUID_TO_BIN(?)
			""", ATTEMPT_PUBLIC_ID.toString());
		assertThat(attempt.get("status")).isEqualTo("SUCCEEDED");
		assertThat(attempt.get("failure_code")).isNull();
	}

	private PaymentOAuthProperties properties() {
		return new PaymentOAuthProperties(
			true,
			PaymentEnvironment.PRODUCTION,
			"client-id-fixture",
			"client-secret-fixture",
			URI.create("https://api.example.test/oauth/callback"),
			URI.create("https://auth.mercadopago.com"),
			URI.create("https://api.mercadopago.com"),
			URI.create("https://api.mercadolibre.com"),
			URI.create("https://app.example.test"),
			Duration.ofSeconds(3),
			Duration.ofSeconds(8),
			"v1",
			"unused-by-test-cipher");
	}

	private PlatformPrincipal principal() {
		return new PlatformPrincipal(new UserCredentials(
			userId, USER_PUBLIC_ID, "owner-a@example.test", "Owner A", "hash",
			UserStatus.ACTIVE));
	}

	private EncryptionContext encryptionContext(String field) {
		return new EncryptionContext(
			TENANT_PUBLIC_ID.toString(), "MERCADO_PAGO", "PRODUCTION",
			ATTEMPT_PUBLIC_ID.toString(), field);
	}

	private Set<String> scopesWithNormalizedLength(int length) {
		int requiredLength = normalize(Set.of("read", "write", "offline_access")).length();
		return Set.of(
			"read", "write", "offline_access",
			"s".repeat(length - requiredLength - 1));
	}

	private String normalize(Set<String> scopes) {
		return String.join(" ", scopes.stream().sorted().toList());
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private static final class PlainTestCipher implements CredentialCipher {
		@Override
		public EncryptedSecret encrypt(String plaintext, EncryptionContext context) {
			return new EncryptedSecret(
				"v1", new byte[12], plaintext.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public String decrypt(EncryptedSecret encryptedSecret, EncryptionContext context) {
			return new String(encryptedSecret.ciphertext(), StandardCharsets.UTF_8);
		}
	}
}
