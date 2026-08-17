package com.comercioflex.platformadmin.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.platformadmin.domain.CompanyActivity;

public record CompanyActivityResponse(
	UUID id,
	String action,
	String actorName,
	String actorEmail,
	Instant createdAt) {

	static CompanyActivityResponse from(CompanyActivity activity) {
		return new CompanyActivityResponse(
			activity.id(), activity.action(), activity.actorName(),
			activity.actorEmail(), activity.createdAt());
	}
}
