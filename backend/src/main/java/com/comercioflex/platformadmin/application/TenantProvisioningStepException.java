package com.comercioflex.platformadmin.application;

public class TenantProvisioningStepException extends RuntimeException {

	private final String safeReason;

	public TenantProvisioningStepException(String safeReason, Throwable cause) {
		super(safeReason, cause);
		this.safeReason = safeReason;
	}

	public String safeReason() {
		return safeReason;
	}
}
