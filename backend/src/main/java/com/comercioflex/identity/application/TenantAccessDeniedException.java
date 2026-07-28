package com.comercioflex.identity.application;

public class TenantAccessDeniedException extends RuntimeException {

	public TenantAccessDeniedException() {
		super("The authenticated user does not have access to this store.");
	}
}
