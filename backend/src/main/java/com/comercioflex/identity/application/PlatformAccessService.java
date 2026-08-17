package com.comercioflex.identity.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.identity.domain.PlatformRole;

@Service
public class PlatformAccessService {

	private final PlatformAccessRepository repository;

	public PlatformAccessService(PlatformAccessRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public PlatformRole roleFor(PlatformPrincipal principal) {
		return repository.findActiveRole(principal.id()).orElse(PlatformRole.USER);
	}

	@Transactional(readOnly = true)
	public boolean hasRole(PlatformPrincipal principal, PlatformRole requiredRole) {
		return repository.findActiveRole(principal.id())
			.filter(requiredRole::equals)
			.isPresent();
	}
}
