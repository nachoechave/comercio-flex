package com.comercioflex.tenant.application;

public final class TenantNotFoundException extends RuntimeException {

	public TenantNotFoundException() {
		super("No active store was found");
	}
}
