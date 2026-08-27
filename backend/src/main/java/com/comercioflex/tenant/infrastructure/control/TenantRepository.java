package com.comercioflex.tenant.infrastructure.control;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

	@Query("""
		SELECT new com.comercioflex.tenant.infrastructure.control.ActiveTenant(
			tenant.id,
			tenant.slug,
			tenant.displayName,
			tenant.databaseKey
		)
		FROM TenantEntity tenant
		WHERE tenant.slug = :slug
			AND tenant.status = com.comercioflex.tenant.infrastructure.control.TenantStatus.ACTIVE
		""")
	Optional<ActiveTenant> findActiveBySlug(@Param("slug") String slug);

	@Query("""
		SELECT new com.comercioflex.tenant.infrastructure.control.ActiveTenant(
			tenant.id,
			tenant.slug,
			tenant.displayName,
			tenant.databaseKey
		)
		FROM TenantEntity tenant
		WHERE tenant.databaseKey = :databaseKey
			AND tenant.status = com.comercioflex.tenant.infrastructure.control.TenantStatus.ACTIVE
		""")
	Optional<ActiveTenant> findActiveByDatabaseKey(@Param("databaseKey") String databaseKey);

	@Query("""
		SELECT new com.comercioflex.tenant.infrastructure.control.ActiveTenant(
			tenant.id,
			tenant.slug,
			tenant.displayName,
			tenant.databaseKey
		)
		FROM TenantEntity tenant
		WHERE tenant.status = com.comercioflex.tenant.infrastructure.control.TenantStatus.ACTIVE
		ORDER BY tenant.slug
		""")
	List<ActiveTenant> findAllActive();
}
