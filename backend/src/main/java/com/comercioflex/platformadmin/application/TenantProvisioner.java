package com.comercioflex.platformadmin.application;

import java.util.UUID;

public interface TenantProvisioner {

	TenantProvisioningCapability capability();

	String databaseNameFor(UUID companyId);

	void provision(PendingCompany company);
}
