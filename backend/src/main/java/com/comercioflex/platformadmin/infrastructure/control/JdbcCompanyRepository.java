package com.comercioflex.platformadmin.infrastructure.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.platformadmin.application.CompanyDashboard;
import com.comercioflex.platformadmin.application.CompanyPage;
import com.comercioflex.platformadmin.application.CompanyRepository;
import com.comercioflex.platformadmin.application.CompanySearch;
import com.comercioflex.platformadmin.application.CompanyStatusFilter;
import com.comercioflex.platformadmin.application.LockedCompany;
import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyStatus;
import com.comercioflex.platformadmin.domain.CompanySummary;
import com.comercioflex.platformadmin.domain.PrimaryAdministrator;

@Repository
public class JdbcCompanyRepository implements CompanyRepository {

	private static final String OWNER_JOINS = """
		LEFT JOIN memberships owner_membership ON owner_membership.id = (
			SELECT membership.id
			FROM memberships membership
			JOIN platform_users candidate_owner ON candidate_owner.id = membership.user_id
			WHERE membership.tenant_id = tenant.id
				AND membership.role = 'OWNER'
				AND membership.status = 'ACTIVE'
				AND candidate_owner.status = 'ACTIVE'
			ORDER BY membership.created_at, membership.id
			LIMIT 1
		)
		LEFT JOIN platform_users owner_user ON owner_user.id = owner_membership.user_id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcCompanyRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public CompanyDashboard dashboard() {
		return jdbcTemplate.queryForObject("""
			SELECT
				COUNT(*) total_companies,
				COALESCE(SUM(status = 'ACTIVE'), 0) active_companies,
				COALESCE(SUM(status = 'SUSPENDED'), 0) suspended_companies,
				COALESCE(SUM(status = 'PROVISIONING'), 0) provisioning_companies,
				COALESCE(SUM(status = 'PROVISIONING_FAILED'), 0) provisioning_failed_companies,
				COALESCE(SUM(status = 'INACTIVE'), 0) inactive_companies
			FROM tenants
			""", (resultSet, rowNumber) -> new CompanyDashboard(
			resultSet.getLong("total_companies"),
			resultSet.getLong("active_companies"),
			resultSet.getLong("suspended_companies"),
			resultSet.getLong("provisioning_companies"),
			resultSet.getLong("provisioning_failed_companies"),
			resultSet.getLong("inactive_companies")));
	}

	@Override
	public CompanyPage findPage(CompanySearch search) {
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> parameters = new ArrayList<>();
		appendFilters(where, parameters, search);
		Long total = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM tenants tenant " + OWNER_JOINS + where,
			Long.class,
			parameters.toArray());

		List<Object> pageParameters = new ArrayList<>(parameters);
		pageParameters.add(search.size());
		pageParameters.add(Math.multiplyExact((long) search.page(), search.size()));
		List<CompanySummary> items = jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(tenant.public_id) public_id,
				tenant.display_name,
				tenant.slug,
				tenant.industry,
				tenant.contact_phone,
				tenant.domain,
				tenant.status,
				tenant.created_at,
				owner_user.display_name owner_name,
				owner_user.email_normalized owner_email
			FROM tenants tenant
			""" + OWNER_JOINS + where
			+ " ORDER BY tenant.created_at DESC, tenant.id DESC LIMIT ? OFFSET ?",
			this::mapSummary,
			pageParameters.toArray());
		return new CompanyPage(
			items, search.page(), search.size(), total == null ? 0 : total);
	}

	@Override
	public Optional<CompanyDetail> findById(UUID companyId) {
		return jdbcTemplate.query("""
			SELECT
				BIN_TO_UUID(tenant.public_id) public_id,
				tenant.display_name,
				tenant.slug,
				tenant.industry,
				tenant.contact_phone,
				tenant.domain,
				tenant.status,
				tenant.created_at,
				owner_user.display_name owner_name,
				owner_user.email_normalized owner_email
			FROM tenants tenant
			""" + OWNER_JOINS + " WHERE tenant.public_id = UUID_TO_BIN(?)",
			(resultSet, rowNumber) -> new CompanyDetail(
				UUID.fromString(resultSet.getString("public_id")),
				resultSet.getString("display_name"),
				resultSet.getString("slug"),
				resultSet.getString("industry"),
				resultSet.getString("contact_phone"),
				CompanyStatus.valueOf(resultSet.getString("status")),
				mapAdministrator(resultSet),
				resultSet.getString("domain"),
				resultSet.getTimestamp("created_at").toInstant(),
				null),
			companyId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public Optional<LockedCompany> lockById(UUID companyId) {
		return jdbcTemplate.query("""
			SELECT id, status
			FROM tenants
			WHERE public_id = UUID_TO_BIN(?)
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new LockedCompany(
				resultSet.getLong("id"),
				CompanyStatus.valueOf(resultSet.getString("status"))),
			companyId.toString())
			.stream()
			.findFirst();
	}

	@Override
	public void updateStatus(long internalId, CompanyStatus status) {
		jdbcTemplate.update(
			"UPDATE tenants SET status = ? WHERE id = ?",
			status.name(),
			internalId);
	}

	@Override
	public void appendStatusAudit(
			long tenantInternalId,
			long actorUserId,
			String action,
			CompanyStatus previousStatus,
			CompanyStatus newStatus) {
		jdbcTemplate.update("""
			INSERT INTO platform_audit_events (
				public_id, actor_user_id, tenant_id, action_name, metadata
			)
			VALUES (
				UUID_TO_BIN(?), ?, ?, ?, JSON_OBJECT(
					'previousStatus', ?, 'newStatus', ?
				)
			)
			""",
			UUID.randomUUID().toString(),
			actorUserId,
			tenantInternalId,
			action,
			previousStatus.name(),
			newStatus.name());
	}

	private void appendFilters(
			StringBuilder where,
			List<Object> parameters,
			CompanySearch search) {
		if (search.status() != CompanyStatusFilter.ALL) {
			where.append(" AND tenant.status = ?");
			parameters.add(search.status().name());
		}
		if (search.query() != null) {
			where.append("""
				 AND (
					LOWER(tenant.display_name) LIKE ?
					OR LOWER(tenant.slug) LIKE ?
					OR LOWER(owner_user.email_normalized) LIKE ?
				)
				""");
			String pattern = "%" + search.query().toLowerCase(java.util.Locale.ROOT) + "%";
			parameters.add(pattern);
			parameters.add(pattern);
			parameters.add(pattern);
		}
	}

	private CompanySummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CompanySummary(
			UUID.fromString(resultSet.getString("public_id")),
			resultSet.getString("display_name"),
			resultSet.getString("slug"),
			CompanyStatus.valueOf(resultSet.getString("status")),
			mapAdministrator(resultSet),
			resultSet.getTimestamp("created_at").toInstant());
	}

	private PrimaryAdministrator mapAdministrator(ResultSet resultSet) throws SQLException {
		String email = resultSet.getString("owner_email");
		return email == null
			? null
			: new PrimaryAdministrator(resultSet.getString("owner_name"), email);
	}
}
