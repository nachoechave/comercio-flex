package com.comercioflex.payment.infrastructure.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.comercioflex.payment.application.QrAuthorizationStatus;
import com.comercioflex.payment.application.QrProvisioningStatus;
import com.comercioflex.payment.application.QrSetupRepository;
import com.comercioflex.payment.application.StoredQrSetup;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Testcontainers
@SpringBootTest
class JdbcQrSetupRepositoryIntegrationTests {

	private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

	@Container
	static final MySQLContainer<?> DATABASE = new MySQLContainer<>(
		DockerImageName.parse("mysql:8.4.10"));

	@Autowired QrSetupRepository repository;
	@Autowired DataSource controlDataSource;

	private JdbcTemplate jdbc;
	private long tenantA;
	private long tenantB;

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
		jdbc.update("DELETE FROM merchant_qr_configurations");
		jdbc.update("DELETE FROM tenants");
		jdbc.update("""
			INSERT INTO tenants (public_id, slug, display_name, status, database_key)
			VALUES
				(UUID_TO_BIN('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
				 'tienda-a', 'Tienda A', 'ACTIVE', 'tenant-a'),
				(UUID_TO_BIN('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
				 'tienda-b', 'Tienda B', 'ACTIVE', 'tenant-b')
			""");
		tenantA = jdbc.queryForObject(
			"SELECT id FROM tenants WHERE slug = 'tienda-a'", Long.class);
		tenantB = jdbc.queryForObject(
			"SELECT id FROM tenants WHERE slug = 'tienda-b'", Long.class);
	}

	@Test
	void storesOneIndependentConfigurationPerTenantAndEnvironment() {
		StoredQrSetup firstA = repository.createIfMissing(
			tenantA, PaymentEnvironment.PRODUCTION, "STOREA", "POSA",
			UUID.fromString("11111111-1111-4111-8111-111111111111"), NOW);
		StoredQrSetup firstB = repository.createIfMissing(
			tenantB, PaymentEnvironment.PRODUCTION, "STOREB", "POSB",
			UUID.fromString("22222222-2222-4222-8222-222222222222"), NOW);

		repository.saveResult(
			firstA, "provider-store-a", "provider-pos-a",
			QrProvisioningStatus.LISTO, QrAuthorizationStatus.AUTHORIZED,
			null, NOW.plusSeconds(1));

		StoredQrSetup loadedA = repository.find(tenantA, PaymentEnvironment.PRODUCTION)
			.orElseThrow();
		StoredQrSetup loadedB = repository.find(tenantB, PaymentEnvironment.PRODUCTION)
			.orElseThrow();
		assertThat(loadedA.status()).isEqualTo(QrProvisioningStatus.LISTO);
		assertThat(loadedA.providerPosId()).isEqualTo("provider-pos-a");
		assertThat(loadedB.status()).isEqualTo(QrProvisioningStatus.NO_CONFIGURADO);
		assertThat(loadedB.providerPosId()).isNull();
		assertThat(firstB.externalPosId()).isEqualTo("POSB");
	}

	@Test
	void retryKeepsTheOriginalExternalIdsAndProviderIdempotencyKey() {
		UUID originalKey = UUID.fromString("11111111-1111-4111-8111-111111111111");
		StoredQrSetup original = repository.createIfMissing(
			tenantA, PaymentEnvironment.PRODUCTION, "STOREA", "POSA", originalKey, NOW);

		StoredQrSetup retried = repository.createIfMissing(
			tenantA, PaymentEnvironment.PRODUCTION, "OTHERSTORE", "OTHERPOS",
			UUID.fromString("33333333-3333-4333-8333-333333333333"), NOW.plusSeconds(5));

		assertThat(retried.id()).isEqualTo(original.id());
		assertThat(retried.externalStoreId()).isEqualTo("STOREA");
		assertThat(retried.externalPosId()).isEqualTo("POSA");
		assertThat(retried.posIdempotencyKey()).isEqualTo(originalKey);
		assertThat(jdbc.queryForObject(
			"SELECT COUNT(*) FROM merchant_qr_configurations", Integer.class)).isOne();
	}
}
