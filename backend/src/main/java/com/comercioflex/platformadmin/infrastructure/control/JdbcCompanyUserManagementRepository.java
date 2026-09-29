package com.comercioflex.platformadmin.infrastructure.control;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.platformadmin.application.CompanyUserManagementRepository;
import com.comercioflex.platformadmin.domain.CompanyUser;

@Repository
public class JdbcCompanyUserManagementRepository implements CompanyUserManagementRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcCompanyUserManagementRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Long> findTenantInternalId(UUID companyId) {
		return jdbcTemplate.query(
			"SELECT id FROM tenants WHERE public_id = UUID_TO_BIN(?)",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			companyId.toString()).stream().findFirst();
	}

	@Override
	public boolean emailExists(String normalizedEmail) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM platform_users WHERE email_normalized = ?",
			Integer.class,
			normalizedEmail);
		return count != null && count > 0;
	}

	@Override
	public long insertUser(
			UUID userId,
			String name,
			String normalizedEmail,
			String passwordHash) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO platform_users (
					public_id, email_normalized, display_name, password_hash,
					status, platform_role, password_changed_at
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, 'ACTIVE', 'USER', ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, userId.toString());
			statement.setString(2, normalizedEmail);
			statement.setString(3, name);
			statement.setString(4, passwordHash);
			statement.setTimestamp(5, Timestamp.from(Instant.now()));
			return statement;
		}, keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Missing generated key for platform user");
		}
		return key.longValue();
	}

	@Override
	public void insertMembership(long userInternalId, long tenantInternalId, MembershipRole role) {
		jdbcTemplate.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			VALUES (?, ?, ?, 'ACTIVE')
			""", userInternalId, tenantInternalId, role.name());
	}

	@Override
	public Optional<CompanyUser> findUser(UUID companyId, UUID userId) {
		return jdbcTemplate.query("""
			SELECT BIN_TO_UUID(user.public_id) public_id,
				user.display_name, user.email_normalized, user.status user_status,
				membership.role, membership.status membership_status,
				membership.created_at
			FROM tenants tenant
			JOIN memberships membership ON membership.tenant_id = tenant.id
			JOIN platform_users user ON user.id = membership.user_id
			WHERE tenant.public_id = UUID_TO_BIN(?)
				AND user.public_id = UUID_TO_BIN(?)
			""", (resultSet, rowNumber) -> new CompanyUser(
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getString("display_name"),
			resultSet.getString("email_normalized"),
			MembershipRole.valueOf(resultSet.getString("role")),
			MembershipStatus.valueOf(resultSet.getString("membership_status")),
			UserStatus.valueOf(resultSet.getString("user_status")),
			resultSet.getTimestamp("created_at").toInstant()),
			companyId.toString(), userId.toString()).stream().findFirst();
	}

	@Override
	public void updateMembership(
			long tenantInternalId,
			UUID userId,
			MembershipRole role,
			MembershipStatus status) {
		jdbcTemplate.update("""
			UPDATE memberships membership
			JOIN platform_users user ON user.id = membership.user_id
			SET membership.role = ?, membership.status = ?
			WHERE membership.tenant_id = ?
				AND user.public_id = UUID_TO_BIN(?)
			""", role.name(), status.name(), tenantInternalId, userId.toString());
	}

	@Override
	public void appendAudit(
			long tenantInternalId,
			long actorUserId,
			String action,
			UUID userId,
			MembershipRole role,
			MembershipStatus status) {
		jdbcTemplate.update("""
			INSERT INTO platform_audit_events (
				public_id, actor_user_id, tenant_id, action_name, metadata
			)
			VALUES (UUID_TO_BIN(?), ?, ?, ?, JSON_OBJECT(
				'userId', ?, 'role', ?, 'membershipStatus', ?
			))
			""", UUID.randomUUID().toString(), actorUserId, tenantInternalId, action,
			userId.toString(), role.name(), status.name());
	}
}
