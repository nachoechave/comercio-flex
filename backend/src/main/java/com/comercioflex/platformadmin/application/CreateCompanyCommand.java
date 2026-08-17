package com.comercioflex.platformadmin.application;

import com.comercioflex.platformadmin.domain.CompanyStatus;

public record CreateCompanyCommand(
	String name,
	String slug,
	String industry,
	String administratorEmail,
	String administratorName,
	String administratorPhone,
	String domain,
	String initialPassword,
	CompanyStatus requestedStatus) {
}
