package com.comercioflex.platformadmin.application;

import com.comercioflex.platformadmin.domain.CompanyStatus;

public record LockedCompany(
	long internalId,
	CompanyStatus status,
	String name,
	String industry,
	String phone,
	String domain) {
}
