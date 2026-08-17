package com.comercioflex.platformadmin.domain;

import java.time.Instant;
import java.util.UUID;

public record CompanySummary(
	UUID id,
	String name,
	String slug,
	CompanyStatus status,
	PrimaryAdministrator primaryAdministrator,
	Instant createdAt) {
}
