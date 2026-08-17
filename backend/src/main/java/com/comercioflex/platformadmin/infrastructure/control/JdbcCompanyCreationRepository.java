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

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.platformadmin.application.CompanyCreationRepository;
import com.comercioflex.platformadmin.application.CreateCompanyCommand;
import com.comercioflex.platformadmin.application.PendingCompany;
import com.comercioflex.platformadmin.domain.CompanyStatus;

@Repository
public class JdbcCompanyCreationRepository implements CompanyCreationRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcCompanyCreationRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public PendingCompany createPending(
			CreateCompanyCommand command,
			String passwordHash,
			PlatformPrincipal actor,
			UUID companyId,
			String databaseKey,
			String databaseName) {
		long tenantId = insertTenant(command, companyId, databaseKey);
		long ownerId = findUser(command.administratorEmail())
			.orElseGet(() -> insertUser(command, passwordHash));
		jdbcTemplate.update("""
			INSERT INTO memberships (user_id, tenant_id, role, status)
			VALUES (?, ?, 'OWNER', 'ACTIVE')
			""", ownerId, tenantId);
		jdbcTemplate.update("""
			INSERT INTO tenant_infrastructure (
				tenant_id, database_name, provisioning_status, requested_tenant_status
			)
			VALUES (?, ?, 'PENDING', ?)
			""", tenantId, databaseName, command.requestedStatus().name());
		appendAudit(actor.id(), tenantId, "COMPANY_PROVISIONING_STARTED", """
			JSON_OBJECT('requestedStatus', ?, 'administratorEmail', ?)
			""", command.requestedStatus().name(), command.administratorEmail());
		return new PendingCompany(
			tenantId,
			companyId,
			databaseKey,
			databaseName,
			command.name(),
			command.administratorEmail(),
			command.administratorPhone(),
			command.requestedStatus());
	}

	@Override
	public Optional<PendingCompany> lockProvisioningCompany(UUID companyId) {
		Optional<PendingCompany> company = jdbcTemplate.query("""
			SELECT
				tenant.id,
				BIN_TO_UUID(tenant.public_id) public_id,
				tenant.database_key,
				infrastructure.database_name,
				tenant.display_name,
				owner.email_normalized owner_email,
				tenant.contact_phone,
				infrastructure.requested_tenant_status
			FROM tenants tenant
			JOIN tenant_infrastructure infrastructure ON infrastructure.tenant_id = tenant.id
			JOIN memberships membership ON membership.tenant_id = tenant.id
				AND membership.role = 'OWNER' AND membership.status = 'ACTIVE'
			JOIN platform_users owner ON owner.id = membership.user_id
			WHERE tenant.public_id = UUID_TO_BIN(?)
				AND tenant.status = 'PROVISIONING_FAILED'
				AND infrastructure.provisioning_status = 'FAILED'
			ORDER BY membership.created_at, membership.id
			LIMIT 1
			FOR UPDATE
			""", (resultSet, rowNumber) -> new PendingCompany(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getString("database_key"),
			resultSet.getString("database_name"),
			resultSet.getString("display_name"),
			resultSet.getString("owner_email"),
			resultSet.getString("contact_phone"),
			CompanyStatus.valueOf(resultSet.getString("requested_tenant_status"))),
			companyId.toString()).stream().findFirst();
		company.ifPresent(pending -> {
			jdbcTemplate.update("""
				UPDATE tenant_infrastructure
				SET provisioning_status = 'PENDING', failure_reason = NULL
				WHERE tenant_id = ?
				""", pending.internalId());
			jdbcTemplate.update(
				"UPDATE tenants SET status = 'PROVISIONING' WHERE id = ?",
				pending.internalId());
		});
		return company;
	}

	@Override
	public void markReady(PendingCompany company, PlatformPrincipal actor) {
		jdbcTemplate.update("""
			UPDATE tenant_infrastructure
			SET provisioning_status = 'READY', failure_reason = NULL,
				provisioned_at = COALESCE(provisioned_at, ?)
			WHERE tenant_id = ?
			""", Timestamp.from(Instant.now()), company.internalId());
		jdbcTemplate.update(
			"UPDATE tenants SET status = ? WHERE id = ?",
			company.requestedStatus().name(),
			company.internalId());
		appendAudit(actor.id(), company.internalId(), "COMPANY_PROVISIONING_COMPLETED", """
			JSON_OBJECT('status', ?)
			""", company.requestedStatus().name());
	}

	@Override
	public void markFailed(PendingCompany company, PlatformPrincipal actor, String reason) {
		jdbcTemplate.update("""
			UPDATE tenant_infrastructure
			SET provisioning_status = 'FAILED', failure_reason = ?
			WHERE tenant_id = ?
			""", reason, company.internalId());
		jdbcTemplate.update(
			"UPDATE tenants SET status = 'PROVISIONING_FAILED' WHERE id = ?",
			company.internalId());
		appendAudit(actor.id(), company.internalId(), "COMPANY_PROVISIONING_FAILED", """
			JSON_OBJECT('reason', ?)
			""", reason);
	}

	private long insertTenant(
			CreateCompanyCommand command,
			UUID companyId,
			String databaseKey) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO tenants (
					public_id, slug, display_name, industry, contact_phone,
					domain, status, database_key
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, 'PROVISIONING', ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, companyId.toString());
			statement.setString(2, command.slug());
			statement.setString(3, command.name());
			statement.setString(4, command.industry());
			statement.setString(5, command.administratorPhone());
			statement.setString(6, command.domain());
			statement.setString(7, databaseKey);
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder, "tenant");
	}

	private Optional<Long> findUser(String email) {
		return jdbcTemplate.query(
			"SELECT id FROM platform_users WHERE email_normalized = ?",
			(resultSet, rowNumber) -> resultSet.getLong("id"),
			email).stream().findFirst();
	}

	private long insertUser(CreateCompanyCommand command, String passwordHash) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO platform_users (
					public_id, email_normalized, display_name, password_hash,
					status, platform_role, password_changed_at
				)
				VALUES (UUID_TO_BIN(?), ?, ?, ?, 'ACTIVE', 'USER', ?)
				""", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, UUID.randomUUID().toString());
			statement.setString(2, command.administratorEmail());
			statement.setString(3, command.administratorName());
			statement.setString(4, passwordHash);
			statement.setTimestamp(5, Timestamp.from(Instant.now()));
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder, "platform user");
	}

	private long requiredKey(GeneratedKeyHolder keyHolder, String resource) {
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Missing generated key for " + resource);
		}
		return key.longValue();
	}

	private void appendAudit(
			long actorId,
			long tenantId,
			String action,
			String metadataExpression,
			Object... metadataParameters) {
		Object[] parameters = new Object[metadataParameters.length + 4];
		parameters[0] = UUID.randomUUID().toString();
		parameters[1] = actorId;
		parameters[2] = tenantId;
		parameters[3] = action;
		System.arraycopy(metadataParameters, 0, parameters, 4, metadataParameters.length);
		jdbcTemplate.update("""
			INSERT INTO platform_audit_events (
				public_id, actor_user_id, tenant_id, action_name, metadata
			)
			VALUES (UUID_TO_BIN(?), ?, ?, ?,
			""" + metadataExpression + ")", parameters);
	}
}
