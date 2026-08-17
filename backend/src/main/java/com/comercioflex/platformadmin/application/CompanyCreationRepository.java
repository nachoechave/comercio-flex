package com.comercioflex.platformadmin.application;

import java.util.Optional;
import java.util.UUID;

import com.comercioflex.identity.application.PlatformPrincipal;

public interface CompanyCreationRepository {

	PendingCompany createPending(
		CreateCompanyCommand command,
		String passwordHash,
		PlatformPrincipal actor,
		UUID companyId,
		String databaseKey,
		String databaseName);

	Optional<PendingCompany> lockProvisioningCompany(UUID companyId);

	void markReady(PendingCompany company, PlatformPrincipal actor);

	void markFailed(PendingCompany company, PlatformPrincipal actor, String reason);
}
