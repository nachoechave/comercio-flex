package com.comercioflex.tenant.infrastructure.control;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantDomainRepository extends JpaRepository<TenantDomainEntity, Long> {

        @Query("""
                SELECT domain.tenant.slug
                FROM TenantDomainEntity domain
                WHERE domain.hostname = :hostname
                        AND domain.verified = true
                        AND domain.tenant.status = com.comercioflex.tenant.infrastructure.control.TenantStatus.ACTIVE
                """)
        Optional<String> findActiveSlugByHostname(@Param("hostname") String hostname);
}
