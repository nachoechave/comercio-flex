package com.comercioflex.platformadmin.infrastructure.control;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.platformadmin.application.BrandingCompany;
import com.comercioflex.platformadmin.application.CompanyBrandingRepository;

@Repository
public class JdbcCompanyBrandingRepository implements CompanyBrandingRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcCompanyBrandingRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<BrandingCompany> findCompany(UUID companyId) {
		return jdbcTemplate.query("""
			SELECT id, BIN_TO_UUID(public_id) public_id, slug, database_key
			FROM tenants
			WHERE public_id = UUID_TO_BIN(?)
			""", (resultSet, rowNumber) -> new BrandingCompany(
			resultSet.getLong("id"),
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getString("slug"),
			resultSet.getString("database_key")),
			companyId.toString()).stream().findFirst();
	}

	@Override
	public void appendAudit(
			long actorId,
			long tenantId,
			String action,
			String assetType,
			String template) {
		jdbcTemplate.update("""
			INSERT INTO platform_audit_events (
				public_id, actor_user_id, tenant_id, action_name, metadata
			)
			VALUES (UUID_TO_BIN(?), ?, ?, ?, JSON_OBJECT(
				'assetType', ?, 'template', ?
			))
			""", UUID.randomUUID().toString(), actorId, tenantId, action, assetType, template);
	}
}
