package com.comercioflex.identity.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.identity.infrastructure.control.MembershipRepository;

@Service
public class SessionViewService {

	private final MembershipRepository membershipRepository;

	public SessionViewService(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Transactional(readOnly = true)
	public List<ActiveMembership> membershipsFor(PlatformPrincipal principal) {
		return membershipRepository.findActiveMemberships(principal.id());
	}
}
