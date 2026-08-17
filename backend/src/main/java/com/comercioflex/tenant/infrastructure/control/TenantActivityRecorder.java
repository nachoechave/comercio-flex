package com.comercioflex.tenant.infrastructure.control;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantActivityRecorder {

	private final JdbcTemplate jdbcTemplate;

	public TenantActivityRecorder(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void recordAdministrativeActivity(long tenantId) {
		jdbcTemplate.update("""
			UPDATE tenants
			SET last_activity_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
				AND (
					last_activity_at IS NULL
					OR last_activity_at < CURRENT_TIMESTAMP(6) - INTERVAL 5 MINUTE
				)
			""", tenantId);
	}
}
