package com.comercioflex.identity.infrastructure.control;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.identity.application.PublicIdentityException;
import com.comercioflex.identity.application.PublicIdentityRepository;
import com.comercioflex.identity.application.PublicProfile;

@Repository
public class JdbcPublicIdentityRepository implements PublicIdentityRepository {

	private final JdbcTemplate jdbc;

	public JdbcPublicIdentityRepository(@Qualifier("controlJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void create(String firstName, String lastName, String email, String phone, String hash) {
		jdbc.update("""
			INSERT INTO platform_users
			(public_id, email_normalized, display_name, first_name, last_name, phone, password_hash, status, platform_role)
			VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, 'ACTIVE', 'USER')
			""", UUID.randomUUID().toString(), email, firstName + " " + lastName, firstName, lastName, phone, hash);
	}

	@Override
	public PublicProfile profile(long userId) {
		return jdbc.query("""
			SELECT COALESCE(first_name, display_name) first_name, COALESCE(last_name, '') last_name,
			       email_normalized, phone FROM platform_users WHERE id = ? AND status = 'ACTIVE'
			""", (rs, n) -> new PublicProfile(rs.getString("first_name"), rs.getString("last_name"),
				rs.getString("email_normalized"), rs.getString("phone")), userId)
			.stream().findFirst().orElseThrow(() -> new PublicIdentityException("La cuenta no está disponible."));
	}

	@Override
	public void update(long userId, String firstName, String lastName, String phone) {
		jdbc.update("""
			UPDATE platform_users SET first_name = ?, last_name = ?, phone = ?, display_name = ?
			WHERE id = ? AND status = 'ACTIVE'
			""", firstName, lastName, phone, firstName + " " + lastName, userId);
	}

	@Override
	public Optional<Long> activeUser(String email) {
		return jdbc.query("SELECT id FROM platform_users WHERE email_normalized = ? AND status = 'ACTIVE'",
			(rs, n) -> rs.getLong(1), email).stream().findFirst();
	}

	@Override
	public void storeReset(long userId, long tenantId, byte[] hash, Instant expiresAt) {
		// Same lock order as reset consumption; a new request supersedes earlier links.
		lockUser(userId);
		jdbc.update("DELETE FROM identity_password_resets WHERE user_id = ?", userId);
		jdbc.update("INSERT INTO identity_password_resets (token_hash, user_id, tenant_id, expires_at) VALUES (?, ?, ?, ?)",
			hash, userId, tenantId, Timestamp.from(expiresAt));
	}

	@Override
	public Optional<Long> resetUser(byte[] hash, long tenantId, Instant now) {
		return jdbc.query("""
			SELECT user_id FROM identity_password_resets WHERE token_hash = ? AND tenant_id = ? AND expires_at > ?
			""", (rs, n) -> rs.getLong(1), hash, tenantId, Timestamp.from(now)).stream().findFirst();
	}

	@Override
	public void lockUser(long userId) {
		jdbc.queryForObject("SELECT id FROM platform_users WHERE id = ? FOR UPDATE", Long.class, userId);
	}

	@Override
	public boolean consumeReset(byte[] hash, long tenantId, Instant now) {
		return jdbc.update("DELETE FROM identity_password_resets WHERE token_hash = ? AND tenant_id = ? AND expires_at > ?",
			hash, tenantId, Timestamp.from(now)) == 1;
	}

	@Override
	public void changePasswordAndRevokeSessions(long userId, String hash) {
		int changed = jdbc.update("UPDATE platform_users SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'ACTIVE'",
			hash, userId);
		if (changed != 1) throw new PublicIdentityException("El enlace no es válido o venció. Solicitá uno nuevo.");
		jdbc.update("DELETE FROM identity_password_resets WHERE user_id = ?", userId);
		jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = (SELECT email_normalized FROM platform_users WHERE id = ?)", userId);
	}
}
