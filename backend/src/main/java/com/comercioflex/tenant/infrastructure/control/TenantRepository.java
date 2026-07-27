package com.comercioflex.tenant.infrastructure.control;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

	@Query("""
		SELECT new com.comercioflex.tenant.infrastructure.control.ActiveTenant(
			tenant.slug,
			tenant.databaseKey
		)
		FROM TenantEntity tenant
		WHERE tenant.slug = :slug
			AND tenant.status = com.comercioflex.tenant.infrastructure.control.TenantStatus.ACTIVE
		""")
	Optional<ActiveTenant> findActiveBySlug(@Param("slug") String slug);
}
