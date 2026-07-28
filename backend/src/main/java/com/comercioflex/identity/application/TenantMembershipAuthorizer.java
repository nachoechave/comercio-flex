package com.comercioflex.identity.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.identity.infrastructure.control.MembershipRepository;

@Service
public class TenantMembershipAuthorizer {

	private final MembershipRepository membershipRepository;

	public TenantMembershipAuthorizer(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Transactional(readOnly = true)
	public TenantMembership requireActiveMembership(Long userId, Long tenantId) {
		return membershipRepository.findActive(userId, tenantId)
			.orElseThrow(TenantAccessDeniedException::new);
	}
}
