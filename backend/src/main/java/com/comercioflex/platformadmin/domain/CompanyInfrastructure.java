package com.comercioflex.platformadmin.domain;

import java.time.Instant;

public record CompanyInfrastructure(
	String isolationMode,
	String provisioningStatus,
	Instant provisionedAt,
	Instant updatedAt,
	boolean customDomainConfigured,
	Instant lastActivityAt) {
}
