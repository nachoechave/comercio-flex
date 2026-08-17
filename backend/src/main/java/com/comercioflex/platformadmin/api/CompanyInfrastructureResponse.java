package com.comercioflex.platformadmin.api;

import java.time.Instant;

import com.comercioflex.platformadmin.domain.CompanyInfrastructure;

public record CompanyInfrastructureResponse(
	String isolationMode,
	String provisioningStatus,
	Instant provisionedAt,
	Instant updatedAt,
	boolean customDomainConfigured,
	Instant lastActivityAt) {

	static CompanyInfrastructureResponse from(CompanyInfrastructure infrastructure) {
		return new CompanyInfrastructureResponse(
			infrastructure.isolationMode(), infrastructure.provisioningStatus(),
			infrastructure.provisionedAt(), infrastructure.updatedAt(),
			infrastructure.customDomainConfigured(), infrastructure.lastActivityAt());
	}
}
