package com.comercioflex.identity.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.identity.domain.PlatformRole;
import com.comercioflex.identity.infrastructure.control.MembershipRepository;

@Service
public class SessionViewService {

	private final MembershipRepository membershipRepository;
	private final PlatformAccessService platformAccessService;

	public SessionViewService(
			MembershipRepository membershipRepository,
			PlatformAccessService platformAccessService) {
		this.membershipRepository = membershipRepository;
		this.platformAccessService = platformAccessService;
	}

	@Transactional(readOnly = true)
	public List<ActiveMembership> membershipsFor(PlatformPrincipal principal) {
		return membershipRepository.findActiveMemberships(principal.id());
	}

	public PlatformRole platformRoleFor(PlatformPrincipal principal) {
		return platformAccessService.roleFor(principal);
	}
}
