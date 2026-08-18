package com.comercioflex.platformadmin.api;

import com.comercioflex.platformadmin.application.TenantProvisioningCapability;

public record TenantProvisioningCapabilityResponse(
	boolean available,
	String provider,
	String reason) {

	static TenantProvisioningCapabilityResponse from(TenantProvisioningCapability capability) {
		return new TenantProvisioningCapabilityResponse(
			capability.available(), capability.provider(), capability.reason());
	}
}
