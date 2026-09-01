package com.comercioflex.payment.infrastructure.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.payment.application.QrAuthorizationStatus;
import com.comercioflex.payment.application.QrProvisioningStatus;
import com.comercioflex.payment.application.QrSetupException;
import com.comercioflex.payment.application.QrSetupRepository;
import com.comercioflex.payment.application.QrSetupTenant;
import com.comercioflex.payment.application.StoredQrSetup;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Repository
public class JdbcQrSetupRepository implements QrSetupRepository {

	private static final String PROVIDER = "MERCADO_PAGO";
	private static final String SELECT = """
		SELECT id, tenant_id, environment, provider_store_id, external_store_id,
			provider_pos_id, external_pos_id, status, authorization_status,
			BIN_TO_UUID(pos_idempotency_key) pos_idempotency_key, version
		FROM merchant_qr_configurations
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcQrSetupRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public QrSetupTenant requireActiveTenant(long tenantId, String tenantSlug) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, slug
			FROM tenants
			WHERE id = ? AND slug = ? AND status = 'ACTIVE'
			""", (resultSet, rowNumber) -> new QrSetupTenant(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getString("slug")), tenantId, tenantSlug)
			.stream()
			.findFirst()
			.orElseThrow(() -> new QrSetupException(
				"QR_TENANT_NOT_FOUND", "No se encontró un comercio activo."));
	}

	@Override
	public Optional<StoredQrSetup> find(
			long tenantId, PaymentEnvironment environment) {
		return jdbcTemplate.query(SELECT + """
			WHERE tenant_id = ? AND provider = ? AND environment = ?
			""", this::map, tenantId, PROVIDER, environment.name())
			.stream()
			.findFirst();
	}

	@Override
	public StoredQrSetup createIfMissing(
			long tenantId,
			PaymentEnvironment environment,
			String externalStoreId,
			String externalPosId,
			UUID posIdempotencyKey,
			Instant now) {
		jdbcTemplate.update("""
			INSERT INTO merchant_qr_configurations (
				public_id, tenant_id, provider, environment,
				external_store_id, external_pos_id, status,
				authorization_status, pos_idempotency_key, created_at, updated_at
			)
			VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, 'NO_CONFIGURADO',
				'NOT_CHECKED', UUID_TO_BIN(?), ?, ?)
			ON DUPLICATE KEY UPDATE id = id
			""", UUID.randomUUID().toString(), tenantId, PROVIDER, environment.name(),
			externalStoreId, externalPosId, posIdempotencyKey.toString(),
			Timestamp.from(now), Timestamp.from(now));
		return find(tenantId, environment).orElseThrow(() -> new QrSetupException(
			"QR_SETUP_PERSISTENCE_FAILED", "No se pudo preparar la configuración QR."));
	}

	@Override
	public boolean claimVerification(
			StoredQrSetup setup, Instant now, Instant staleBefore) {
		return jdbcTemplate.update("""
			UPDATE merchant_qr_configurations
			SET status = 'VERIFICANDO', authorization_status = 'NOT_CHECKED',
				verification_started_at = ?, last_error_code = NULL,
				version = version + 1, updated_at = ?
			WHERE id = ?
				AND (status <> 'VERIFICANDO' OR verification_started_at < ?)
			""", Timestamp.from(now), Timestamp.from(now), setup.id(),
			Timestamp.from(staleBefore)) == 1;
	}

	@Override
	public void saveResult(
			StoredQrSetup setup,
			String providerStoreId,
			String providerPosId,
			QrProvisioningStatus status,
			QrAuthorizationStatus authorization,
			String safeErrorCode,
			Instant now) {
		int updated = jdbcTemplate.update("""
			UPDATE merchant_qr_configurations
			SET provider_store_id = ?, provider_pos_id = ?, status = ?,
				authorization_status = ?, verification_started_at = NULL,
				last_error_code = ?, version = version + 1, updated_at = ?
			WHERE id = ?
			""", providerStoreId, providerPosId, status.name(), authorization.name(),
			safeErrorCode, Timestamp.from(now), setup.id());
		if (updated != 1) {
			throw new QrSetupException(
				"QR_SETUP_PERSISTENCE_FAILED", "No se pudo guardar la configuración QR.");
		}
	}

	private StoredQrSetup map(ResultSet resultSet, int rowNumber) throws SQLException {
		return new StoredQrSetup(
			resultSet.getLong("id"),
			resultSet.getLong("tenant_id"),
			PaymentEnvironment.valueOf(resultSet.getString("environment")),
			resultSet.getString("provider_store_id"),
			resultSet.getString("external_store_id"),
			resultSet.getString("provider_pos_id"),
			resultSet.getString("external_pos_id"),
			QrProvisioningStatus.valueOf(resultSet.getString("status")),
			QrAuthorizationStatus.valueOf(resultSet.getString("authorization_status")),
			UUID.fromString(resultSet.getString("pos_idempotency_key")),
			resultSet.getLong("version"));
	}
}
