package com.comercioflex.platformadmin.api;

import com.comercioflex.platformadmin.application.CompanyDashboard;

public record CompanyDashboardResponse(
	long totalCompanies,
	long activeCompanies,
	long suspendedCompanies,
	long provisioningCompanies,
	long provisioningFailedCompanies,
	long inactiveCompanies) {

	static CompanyDashboardResponse from(CompanyDashboard dashboard) {
		return new CompanyDashboardResponse(
			dashboard.totalCompanies(),
			dashboard.activeCompanies(),
			dashboard.suspendedCompanies(),
			dashboard.provisioningCompanies(),
			dashboard.provisioningFailedCompanies(),
			dashboard.inactiveCompanies());
	}
}
