package com.comercioflex.identity.infrastructure.control;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.comercioflex.identity.application.ActiveMembership;
import com.comercioflex.identity.application.TenantMembership;

public interface MembershipRepository extends JpaRepository<MembershipEntity, Long> {

	boolean existsByUserIdAndTenantId(Long userId, Long tenantId);

	@Query("""
		SELECT new com.comercioflex.identity.application.ActiveMembership(
			tenant.slug,
			tenant.displayName,
			membership.role
		)
		FROM MembershipEntity membership, TenantEntity tenant, PlatformUserEntity user
		WHERE membership.tenantId = tenant.id
			AND membership.userId = :userId
			AND user.id = membership.userId
			AND user.status = com.comercioflex.identity.domain.UserStatus.ACTIVE
			AND membership.status = com.comercioflex.identity.domain.MembershipStatus.ACTIVE
			AND tenant.status = com.comercioflex.tenant.infrastructure.control.TenantStatus.ACTIVE
		ORDER BY tenant.displayName
		""")
	List<ActiveMembership> findActiveMemberships(@Param("userId") Long userId);

	@Query("""
		SELECT new com.comercioflex.identity.application.TenantMembership(
			membership.userId,
			membership.tenantId,
			membership.role
		)
		FROM MembershipEntity membership, PlatformUserEntity user
		WHERE membership.userId = :userId
			AND membership.tenantId = :tenantId
			AND user.id = membership.userId
			AND user.status = com.comercioflex.identity.domain.UserStatus.ACTIVE
			AND membership.status = com.comercioflex.identity.domain.MembershipStatus.ACTIVE
		""")
	Optional<TenantMembership> findActive(
		@Param("userId") Long userId,
		@Param("tenantId") Long tenantId);
}
