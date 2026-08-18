package com.comercioflex.platformadmin.application;

public record TenantProvisioningCapability(
	boolean available,
	String provider,
	String reason) {

	public static TenantProvisioningCapability available(String provider) {
		return new TenantProvisioningCapability(true, provider, null);
	}

	public static TenantProvisioningCapability unavailable(String provider, String reason) {
		return new TenantProvisioningCapability(false, provider, reason);
	}
}
