package com.comercioflex.platformadmin.domain;

import java.time.Instant;
import java.util.UUID;

public record CompanyActivity(
	UUID id,
	String action,
	String actorName,
	String actorEmail,
	Instant createdAt) {
}
