package com.comercioflex.platformadmin.application;

import java.util.Optional;
import java.util.UUID;

import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyStatus;

public interface CompanyRepository {

	CompanyDashboard dashboard();

	CompanyPage findPage(CompanySearch search);

	Optional<CompanyDetail> findById(UUID companyId);

	Optional<LockedCompany> lockById(UUID companyId);

	void updateStatus(long internalId, CompanyStatus status);

	void appendStatusAudit(
		long tenantInternalId,
		long actorUserId,
		String action,
		CompanyStatus previousStatus,
		CompanyStatus newStatus);
}
