package com.comercioflex.identity.application;

import com.comercioflex.identity.domain.MembershipRole;

public record TenantMembership(
	Long userId,
	Long tenantId,
	MembershipRole role) {
}
