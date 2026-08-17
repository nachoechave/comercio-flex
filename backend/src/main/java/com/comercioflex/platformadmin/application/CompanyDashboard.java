package com.comercioflex.platformadmin.application;

public record CompanyDashboard(
	long totalCompanies,
	long activeCompanies,
	long suspendedCompanies,
	long provisioningCompanies,
	long provisioningFailedCompanies,
	long inactiveCompanies) {
}
