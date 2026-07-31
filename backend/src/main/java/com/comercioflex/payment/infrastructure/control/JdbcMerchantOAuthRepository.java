package com.comercioflex.payment.infrastructure.control;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.comercioflex.payment.application.ClaimedOAuthAttempt;
import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.payment.application.MerchantOAuthRepository;
import com.comercioflex.payment.application.OAuthTenantIdentity;
import com.comercioflex.payment.application.PaymentOAuthException;
import com.comercioflex.payment.application.StoredMerchantConnection;
import com.comercioflex.payment.domain.MerchantConnectionStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Repository
public class JdbcMerchantOAuthRepository implements MerchantOAuthRepository {

	private static final String CONNECTION_SELECT = """
		SELECT connection.id,
			BIN_TO_UUID(connection.public_id) public_id,
			connection.tenant_id,
			BIN_TO_UUID(tenant.public_id) tenant_public_id,
			connection.environment,
			connection.status,
			connection.seller_account_id,
			connection.seller_nickname,
			connection.access_token_ciphertext,
			connection.access_token_nonce,
			connection.access_token_key_id,
			connection.refresh_token_ciphertext,
			connection.refresh_token_nonce,
			connection.refresh_token_key_id,
			connection.access_token_expires_at,
			connection.connected_at,
			connection.version
		FROM merchant_payment_connections connection
		JOIN tenants tenant ON tenant.id = connection.tenant_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcMerchantOAuthRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public OAuthTenantIdentity requireActiveTenant(long tenantId, String slug) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, slug
			FROM tenants
			WHERE id = ? AND slug = ? AND status = 'ACTIVE'
			""",
			(resultSet, rowNumber) -> new OAuthTenantIdentity(
				resultSet.getLong("id"),
				UUID.fromString(resultSet.getString("public_id")),
				resultSet.getString("slug")),
			tenantId,
			slug)
			.stream()
			.findFirst()
			.orElseThrow(() -> new PaymentOAuthException(
				"TENANT_NOT_AVAILABLE", "El comercio no está disponible."));
	}

	@Override
	public void supersedePending(
			long tenantId,
			long userId,
			PaymentEnvironment environment,
			Instant now) {
		jdbcTemplate.update("""
			UPDATE payment_oauth_attempts
			SET status = 'EXPIRED',
				pkce_verifier_ciphertext = NULL,
				pkce_verifier_nonce = NULL,
				pkce_verifier_key_id = NULL,
				completed_at = ?,
				version = version + 1
			WHERE tenant_id = ? AND initiated_by_user_id = ?
				AND provider = 'MERCADO_PAGO' AND environment = ?
				AND status = 'PENDING' AND expires_at <= ?
			""",
			Timestamp.from(now), tenantId, userId, environment.name(), Timestamp.from(now));
		jdbcTemplate.update("""
			UPDATE payment_oauth_attempts
			SET status = 'SUPERSEDED',
				pkce_verifier_ciphertext = NULL,
				pkce_verifier_nonce = NULL,
				pkce_verifier_key_id = NULL,
				completed_at = ?,
				version = version + 1
			WHERE tenant_id = ? AND initiated_by_user_id = ?
				AND provider = 'MERCADO_PAGO' AND environment = ?
				AND status = 'PENDING'
			""",
			Timestamp.from(now), tenantId, userId, environment.name());
	}

	@Override
	public void insertAttempt(
			UUID attemptId,
			long tenantId,
			long userId,
			UUID userPublicId,
			PaymentEnvironment environment,
			byte[] stateHash,
			EncryptedSecret pkceVerifier,
			Instant expiresAt) {
		jdbcTemplate.update("""
			INSERT INTO payment_oauth_attempts (
				public_id, tenant_id, initiated_by_user_id,
				initiated_by_user_public_id, initiated_by_role,
				provider, environment, state_hash,
				pkce_verifier_ciphertext, pkce_verifier_nonce,
				pkce_verifier_key_id, status, expires_at
			)
			VALUES (
				UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), 'OWNER',
				'MERCADO_PAGO', ?, ?, ?, ?, ?, 'PENDING', ?
			)
			""",
			attemptId.toString(), tenantId, userId, userPublicId.toString(),
			environment.name(), stateHash, pkceVerifier.ciphertext(),
			pkceVerifier.nonce(), pkceVerifier.keyId(), Timestamp.from(expiresAt));
	}

	@Override
	public Optional<ClaimedOAuthAttempt> claimAttempt(
			byte[] stateHash,
			long currentUserId,
			PaymentEnvironment environment,
			Instant now) {
		Optional<ClaimedOAuthAttempt> attempt = jdbcTemplate.query("""
			SELECT attempt.id,
				BIN_TO_UUID(attempt.public_id) public_id,
				attempt.tenant_id,
				BIN_TO_UUID(tenant.public_id) tenant_public_id,
				tenant.slug tenant_slug,
				attempt.initiated_by_user_id,
				BIN_TO_UUID(attempt.initiated_by_user_public_id) user_public_id,
				attempt.environment,
				attempt.pkce_verifier_ciphertext,
				attempt.pkce_verifier_nonce,
				attempt.pkce_verifier_key_id,
				attempt.expires_at,
				attempt.version
			FROM payment_oauth_attempts attempt
			JOIN tenants tenant ON tenant.id = attempt.tenant_id
			JOIN memberships membership
				ON membership.tenant_id = attempt.tenant_id
				AND membership.user_id = attempt.initiated_by_user_id
			JOIN platform_users platform_user
				ON platform_user.id = attempt.initiated_by_user_id
			WHERE attempt.state_hash = ?
				AND attempt.initiated_by_user_id = ?
				AND attempt.provider = 'MERCADO_PAGO'
				AND attempt.environment = ?
				AND attempt.status = 'PENDING'
				AND attempt.expires_at > ?
				AND tenant.status = 'ACTIVE'
				AND membership.status = 'ACTIVE'
				AND membership.role = 'OWNER'
				AND platform_user.status = 'ACTIVE'
			FOR UPDATE
			""",
			this::mapAttempt,
			stateHash, currentUserId, environment.name(), Timestamp.from(now))
			.stream()
			.findFirst();
		if (attempt.isEmpty()) {
			return Optional.empty();
		}
		ClaimedOAuthAttempt claimed = attempt.get();
		int changed = jdbcTemplate.update("""
			UPDATE payment_oauth_attempts
			SET status = 'PROCESSING', claimed_at = ?, version = version + 1
			WHERE id = ? AND version = ? AND status = 'PENDING'
			""",
			Timestamp.from(now), claimed.internalId(), claimed.version());
		if (changed != 1) {
			return Optional.empty();
		}
		return Optional.of(new ClaimedOAuthAttempt(
			claimed.internalId(), claimed.publicId(), claimed.tenantId(),
			claimed.tenantPublicId(), claimed.tenantSlug(),
			claimed.initiatedByUserId(), claimed.initiatedByUserPublicId(),
			claimed.environment(), claimed.pkceVerifier(), claimed.expiresAt(),
			claimed.version() + 1));
	}

	@Override
	public void markAttemptFailed(long attemptInternalId, String failureCode, Instant now) {
		jdbcTemplate.update("""
			UPDATE payment_oauth_attempts
			SET status = 'FAILED', failure_code = ?,
				pkce_verifier_ciphertext = NULL,
				pkce_verifier_nonce = NULL,
				pkce_verifier_key_id = NULL,
				completed_at = ?, version = version + 1
			WHERE id = ? AND status = 'PROCESSING'
			""",
			failureCode, Timestamp.from(now), attemptInternalId);
	}

	@Override
	public void markAttemptSucceeded(long attemptInternalId, Instant now) {
		int changed = jdbcTemplate.update("""
			UPDATE payment_oauth_attempts
			SET status = 'SUCCEEDED',
				pkce_verifier_ciphertext = NULL,
				pkce_verifier_nonce = NULL,
				pkce_verifier_key_id = NULL,
				completed_at = ?, version = version + 1
			WHERE id = ? AND status = 'PROCESSING'
			""",
			Timestamp.from(now), attemptInternalId);
		if (changed != 1) {
			throw new PaymentOAuthException(
				"OAUTH_REPLAY", "La autorización ya fue procesada.");
		}
	}

	@Override
	public boolean hasPendingAttempt(
			long tenantId,
			PaymentEnvironment environment,
			Instant now) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM payment_oauth_attempts
			WHERE tenant_id = ? AND provider = 'MERCADO_PAGO'
				AND environment = ? AND status IN ('PENDING', 'PROCESSING')
				AND expires_at > ?
			""", Integer.class, tenantId, environment.name(), Timestamp.from(now));
		return count != null && count > 0;
	}

	@Override
	public Optional<StoredMerchantConnection> findConnection(
			long tenantId,
			PaymentEnvironment environment,
			boolean forUpdate) {
		return queryConnection(
			CONNECTION_SELECT + """
			 WHERE connection.tenant_id = ?
				AND connection.provider = 'MERCADO_PAGO'
				AND connection.environment = ?
			""" + (forUpdate ? " FOR UPDATE" : ""),
			tenantId, environment.name());
	}

	@Override
	public Optional<StoredMerchantConnection> findActiveBySeller(
			String sellerAccountId,
			PaymentEnvironment environment,
			boolean forUpdate) {
		return queryConnection(
			CONNECTION_SELECT + """
			 WHERE connection.provider = 'MERCADO_PAGO'
				AND connection.environment = ?
				AND connection.active_seller_account_id = ?
			""" + (forUpdate ? " FOR UPDATE" : ""),
			environment.name(), sellerAccountId);
	}

	@Override
	public long upsertConnected(
			UUID connectionId,
			long tenantId,
			PaymentEnvironment environment,
			String sellerAccountId,
			String sellerNickname,
			Set<String> scopes,
			EncryptedSecret accessToken,
			EncryptedSecret refreshToken,
			Instant accessTokenExpiresAt,
			long connectedByUserId,
			UUID connectedByUserPublicId,
			UUID oauthAttemptId,
			Instant now,
			Optional<StoredMerchantConnection> existing) {
		long internalId;
		if (existing.isPresent()) {
			StoredMerchantConnection stored = existing.get();
			int changed = jdbcTemplate.update("""
				UPDATE merchant_payment_connections
				SET status = 'CONNECTED', seller_account_id = ?, seller_nickname = ?,
					granted_scopes = ?,
					access_token_ciphertext = ?, access_token_nonce = ?,
					access_token_key_id = ?, refresh_token_ciphertext = ?,
					refresh_token_nonce = ?, refresh_token_key_id = ?,
					access_token_expires_at = ?, connected_by_user_id = ?,
					connected_by_user_public_id = UUID_TO_BIN(?),
					connected_by_role = 'OWNER', oauth_attempt_public_id = UUID_TO_BIN(?),
					connected_at = ?, last_refreshed_at = NULL,
					disconnected_at = NULL, last_error_code = NULL,
					version = version + 1
				WHERE id = ? AND version = ?
				""",
				sellerAccountId, sellerNickname,
				String.join(" ", scopes.stream().sorted().toList()),
				accessToken.ciphertext(), accessToken.nonce(), accessToken.keyId(),
				refreshToken.ciphertext(), refreshToken.nonce(), refreshToken.keyId(),
				Timestamp.from(accessTokenExpiresAt), connectedByUserId,
				connectedByUserPublicId.toString(), oauthAttemptId.toString(),
				Timestamp.from(now), stored.internalId(), stored.version());
			if (changed != 1) {
				throw new PaymentOAuthException(
					"CONNECTION_CHANGED", "La conexión cambió durante la operación.");
			}
			internalId = stored.internalId();
		}
		else {
			KeyHolder keys = new GeneratedKeyHolder();
			jdbcTemplate.update(connection -> {
				PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO merchant_payment_connections (
						public_id, tenant_id, provider, environment, status,
						seller_account_id, seller_nickname, granted_scopes,
						access_token_ciphertext, access_token_nonce, access_token_key_id,
						refresh_token_ciphertext, refresh_token_nonce, refresh_token_key_id,
						access_token_expires_at, connected_by_user_id,
						connected_by_user_public_id, connected_by_role,
						oauth_attempt_public_id, connected_at
					)
					VALUES (
						UUID_TO_BIN(?), ?, 'MERCADO_PAGO', ?, 'CONNECTED',
						?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UUID_TO_BIN(?), 'OWNER',
						UUID_TO_BIN(?), ?
					)
					""", Statement.RETURN_GENERATED_KEYS);
				statement.setString(1, connectionId.toString());
				statement.setLong(2, tenantId);
				statement.setString(3, environment.name());
				statement.setString(4, sellerAccountId);
				statement.setString(5, sellerNickname);
				statement.setString(
					6, String.join(" ", scopes.stream().sorted().toList()));
				statement.setBytes(7, accessToken.ciphertext());
				statement.setBytes(8, accessToken.nonce());
				statement.setString(9, accessToken.keyId());
				statement.setBytes(10, refreshToken.ciphertext());
				statement.setBytes(11, refreshToken.nonce());
				statement.setString(12, refreshToken.keyId());
				statement.setTimestamp(13, Timestamp.from(accessTokenExpiresAt));
				statement.setLong(14, connectedByUserId);
				statement.setString(15, connectedByUserPublicId.toString());
				statement.setString(16, oauthAttemptId.toString());
				statement.setTimestamp(17, Timestamp.from(now));
				return statement;
			}, keys);
			Number key = keys.getKey();
			if (key == null) {
				throw new IllegalStateException("No se pudo crear la conexión de pago.");
			}
			internalId = key.longValue();
		}
		insertEvent(tenantId, connectionId, oauthAttemptId, environment,
			"CONNECTED", "USER", connectedByUserId, connectedByUserPublicId,
			"OWNER", null);
		return internalId;
	}

	@Override
	public void disconnect(
			StoredMerchantConnection connection,
			long actorUserId,
			UUID actorUserPublicId,
			Instant now) {
		int changed = jdbcTemplate.update("""
			UPDATE merchant_payment_connections
			SET status = 'DISCONNECTED',
				access_token_ciphertext = NULL, access_token_nonce = NULL,
				access_token_key_id = NULL, refresh_token_ciphertext = NULL,
				refresh_token_nonce = NULL, refresh_token_key_id = NULL,
				access_token_expires_at = NULL, disconnected_at = ?,
				last_error_code = NULL, version = version + 1
			WHERE id = ? AND version = ?
			""", Timestamp.from(now), connection.internalId(), connection.version());
		if (changed != 1) {
			throw new PaymentOAuthException(
				"CONNECTION_CHANGED", "La conexión cambió durante la operación.");
		}
		insertEvent(connection.tenantId(), connection.publicId(), null,
			connection.environment(), "DISCONNECTED", "USER", actorUserId,
			actorUserPublicId, "OWNER", "LOCAL_DISCONNECT");
	}

	@Override
	public void replaceRefreshedTokens(
			StoredMerchantConnection connection,
			Set<String> scopes,
			EncryptedSecret accessToken,
			EncryptedSecret refreshToken,
			Instant accessTokenExpiresAt,
			Instant now) {
		int changed = jdbcTemplate.update("""
			UPDATE merchant_payment_connections
			SET granted_scopes = ?, access_token_ciphertext = ?,
				access_token_nonce = ?, access_token_key_id = ?,
				refresh_token_ciphertext = ?, refresh_token_nonce = ?,
				refresh_token_key_id = ?, access_token_expires_at = ?,
				last_refreshed_at = ?, last_error_code = NULL,
				version = version + 1
			WHERE id = ? AND version = ? AND status = 'CONNECTED'
			""",
			String.join(" ", scopes.stream().sorted().toList()),
			accessToken.ciphertext(), accessToken.nonce(), accessToken.keyId(),
			refreshToken.ciphertext(), refreshToken.nonce(), refreshToken.keyId(),
			Timestamp.from(accessTokenExpiresAt), Timestamp.from(now),
			connection.internalId(), connection.version());
		if (changed != 1) {
			throw new PaymentOAuthException(
				"CONNECTION_CHANGED", "La conexión cambió durante la renovación.");
		}
		insertEvent(connection.tenantId(), connection.publicId(), null,
			connection.environment(), "REFRESHED", "SYSTEM", null, null, null, null);
	}

	@Override
	public void requireReauthorization(
			StoredMerchantConnection connection,
			String errorCode,
			Instant now) {
		int changed = jdbcTemplate.update("""
			UPDATE merchant_payment_connections
			SET status = 'REAUTHORIZATION_REQUIRED',
				access_token_ciphertext = NULL, access_token_nonce = NULL,
				access_token_key_id = NULL, refresh_token_ciphertext = NULL,
				refresh_token_nonce = NULL, refresh_token_key_id = NULL,
				access_token_expires_at = NULL, last_error_code = ?,
				version = version + 1
			WHERE id = ? AND version = ? AND status = 'CONNECTED'
			""", errorCode, connection.internalId(), connection.version());
		if (changed != 1) {
			throw new PaymentOAuthException(
				"CONNECTION_CHANGED", "La conexión cambió durante la renovación.");
		}
		insertEvent(connection.tenantId(), connection.publicId(), null,
			connection.environment(), "REAUTHORIZATION_REQUIRED", "SYSTEM",
			null, null, null, errorCode);
	}

	private Optional<StoredMerchantConnection> queryConnection(
			String sql,
			Object... arguments) {
		return jdbcTemplate.query(sql, this::mapConnection, arguments)
			.stream()
			.findFirst();
	}

	private ClaimedOAuthAttempt mapAttempt(ResultSet resultSet, int rowNumber)
			throws SQLException {
		return new ClaimedOAuthAttempt(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("tenant_id"),
			UUID.fromString(resultSet.getString("tenant_public_id")),
			resultSet.getString("tenant_slug"),
			resultSet.getLong("initiated_by_user_id"),
			UUID.fromString(resultSet.getString("user_public_id")),
			PaymentEnvironment.valueOf(resultSet.getString("environment")),
			new EncryptedSecret(
				resultSet.getString("pkce_verifier_key_id"),
				resultSet.getBytes("pkce_verifier_nonce"),
				resultSet.getBytes("pkce_verifier_ciphertext")),
			resultSet.getTimestamp("expires_at").toInstant(),
			resultSet.getLong("version"));
	}

	private StoredMerchantConnection mapConnection(ResultSet resultSet, int rowNumber)
			throws SQLException {
		MerchantConnectionStatus status = MerchantConnectionStatus.valueOf(
			resultSet.getString("status"));
		return new StoredMerchantConnection(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getLong("tenant_id"),
			UUID.fromString(resultSet.getString("tenant_public_id")),
			PaymentEnvironment.valueOf(resultSet.getString("environment")),
			status,
			resultSet.getString("seller_account_id"),
			resultSet.getString("seller_nickname"),
			encrypted(resultSet, "access_token"),
			encrypted(resultSet, "refresh_token"),
			instant(resultSet, "access_token_expires_at"),
			instant(resultSet, "connected_at"),
			resultSet.getLong("version"));
	}

	private EncryptedSecret encrypted(ResultSet resultSet, String prefix)
			throws SQLException {
		byte[] ciphertext = resultSet.getBytes(prefix + "_ciphertext");
		if (ciphertext == null) {
			return null;
		}
		return new EncryptedSecret(
			resultSet.getString(prefix + "_key_id"),
			resultSet.getBytes(prefix + "_nonce"),
			ciphertext);
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private void insertEvent(
			long tenantId,
			UUID connectionId,
			UUID attemptId,
			PaymentEnvironment environment,
			String eventType,
			String actorType,
			Long actorUserId,
			UUID actorUserPublicId,
			String actorRole,
			String reasonCode) {
		jdbcTemplate.update("""
			INSERT INTO merchant_payment_connection_events (
				public_id, tenant_id, connection_public_id,
				oauth_attempt_public_id, provider, environment, event_type,
				actor_type, actor_user_id, actor_user_public_id,
				actor_role, reason_code
			)
			VALUES (
				UUID_TO_BIN(?), ?, UUID_TO_BIN(?), UUID_TO_BIN(?),
				'MERCADO_PAGO', ?, ?, ?, ?, UUID_TO_BIN(?), ?, ?
			)
			""",
			UUID.randomUUID().toString(), tenantId, connectionId.toString(),
			attemptId == null ? null : attemptId.toString(), environment.name(),
			eventType, actorType, actorUserId,
			actorUserPublicId == null ? null : actorUserPublicId.toString(),
			actorRole, reasonCode);
	}
}
