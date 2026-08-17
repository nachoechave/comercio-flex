package com.comercioflex.platformadmin.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyStatus;

public record CompanyDetailResponse(
	UUID id,
	String name,
	String slug,
	String industry,
	String phone,
	CompanyStatus status,
	PrimaryAdministratorResponse primaryAdministrator,
	String domain,
	Instant createdAt,
	Instant lastActivityAt) {

	static CompanyDetailResponse from(CompanyDetail company) {
		return new CompanyDetailResponse(
			company.id(),
			company.name(),
			company.slug(),
			company.industry(),
			company.phone(),
			company.status(),
			PrimaryAdministratorResponse.from(company.primaryAdministrator()),
			company.domain(),
			company.createdAt(),
			company.lastActivityAt());
	}
}
