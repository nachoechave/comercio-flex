package com.comercioflex.platformadmin.domain;

import com.comercioflex.tenant.domain.TenantType;

import java.time.Instant;
import java.util.UUID;

public record CompanyDetail(
	UUID id,
	String name,
	String slug,
	String industry,
	String phone,
	CompanyStatus status,
	PrimaryAdministrator primaryAdministrator,
	String domain,
	Instant createdAt,
	Instant lastActivityAt,
	TenantType tenantType) {

	public CompanyDetail(UUID id, String name, String slug, String industry, String phone,
		CompanyStatus status, PrimaryAdministrator primaryAdministrator, String domain,
		Instant createdAt, Instant lastActivityAt) {
		this(id, name, slug, industry, phone, status, primaryAdministrator, domain,
			createdAt, lastActivityAt, TenantType.ECOMMERCE);
	}
}
