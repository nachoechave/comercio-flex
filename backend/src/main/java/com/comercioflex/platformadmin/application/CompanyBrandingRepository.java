package com.comercioflex.platformadmin.application;

import java.util.Optional;
import java.util.UUID;

public interface CompanyBrandingRepository {

	Optional<BrandingCompany> findCompany(UUID companyId);

	void appendAudit(long actorId, long tenantId, String action, String assetType, String template);
}
