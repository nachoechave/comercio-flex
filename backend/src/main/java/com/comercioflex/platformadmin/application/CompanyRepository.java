package com.comercioflex.platformadmin.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.platformadmin.domain.CompanyActivity;
import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyInfrastructure;
import com.comercioflex.platformadmin.domain.CompanyStatus;
import com.comercioflex.platformadmin.domain.CompanyUser;

public interface CompanyRepository {

	CompanyDashboard dashboard();

	CompanyPage findPage(CompanySearch search);

	Optional<CompanyDetail> findById(UUID companyId);

	Optional<LockedCompany> lockById(UUID companyId);

	List<CompanyUser> findUsers(UUID companyId);

	CompanyActivityPage findActivity(UUID companyId, int page, int size);

	Optional<CompanyInfrastructure> findInfrastructure(UUID companyId);

	void updateDetails(long internalId, UpdateCompanyCommand command);

	void updateStatus(long internalId, CompanyStatus status);

	void appendStatusAudit(
		long tenantInternalId,
		long actorUserId,
		String action,
		CompanyStatus previousStatus,
		CompanyStatus newStatus);

	void appendDetailsAudit(
		long tenantInternalId,
		long actorUserId,
		UpdateCompanyCommand command);
}
