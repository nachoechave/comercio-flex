package com.comercioflex.platformadmin.application;

import com.comercioflex.tenant.domain.TenantType;

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
	CompanyStatus requestedStatus,
	TenantType tenantType) {

	public CreateCompanyCommand {
		if (tenantType == null) tenantType = TenantType.ECOMMERCE;
	}

	public CreateCompanyCommand(String name, String slug, String industry, String administratorEmail,
		String administratorName, String administratorPhone, String domain, String initialPassword,
		CompanyStatus requestedStatus) {
		this(name, slug, industry, administratorEmail, administratorName, administratorPhone,
			domain, initialPassword, requestedStatus, TenantType.ECOMMERCE);
	}
}
