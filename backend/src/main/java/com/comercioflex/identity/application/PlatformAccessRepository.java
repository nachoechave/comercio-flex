package com.comercioflex.identity.application;

import java.util.Optional;

import com.comercioflex.identity.domain.PlatformRole;

public interface PlatformAccessRepository {

	Optional<PlatformRole> findActiveRole(Long userId);
}
