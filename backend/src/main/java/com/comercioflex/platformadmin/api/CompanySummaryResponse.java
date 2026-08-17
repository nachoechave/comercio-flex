package com.comercioflex.platformadmin.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.platformadmin.domain.CompanyStatus;
import com.comercioflex.platformadmin.domain.CompanySummary;

public record CompanySummaryResponse(
	UUID id,
	String name,
	String slug,
	CompanyStatus status,
	PrimaryAdministratorResponse primaryAdministrator,
	Instant createdAt) {

	static CompanySummaryResponse from(CompanySummary company) {
		return new CompanySummaryResponse(
			company.id(),
			company.name(),
			company.slug(),
			company.status(),
			PrimaryAdministratorResponse.from(company.primaryAdministrator()),
			company.createdAt());
	}
}
