package com.comercioflex.identity.infrastructure.control;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.comercioflex.identity.application.PlatformAccessRepository;
import com.comercioflex.identity.domain.PlatformRole;

@Repository
public class JdbcPlatformAccessRepository implements PlatformAccessRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcPlatformAccessRepository(
			@Qualifier("controlJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<PlatformRole> findActiveRole(Long userId) {
		return jdbcTemplate.query("""
			SELECT platform_role
			FROM platform_users
			WHERE id = ? AND status = 'ACTIVE'
			""",
			(resultSet, rowNumber) -> PlatformRole.valueOf(resultSet.getString("platform_role")),
			userId)
			.stream()
			.findFirst();
	}
}
