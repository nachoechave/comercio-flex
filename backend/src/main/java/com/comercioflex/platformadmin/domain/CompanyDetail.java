package com.comercioflex.platformadmin.domain;

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
	Instant lastActivityAt) {
}
