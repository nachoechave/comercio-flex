package com.comercioflex.payment.application;

public class PaymentOAuthCallbackException extends PaymentOAuthException {

	private final String tenantSlug;

	public PaymentOAuthCallbackException(
			String tenantSlug,
			String code,
			String message,
			Throwable cause) {
		super(code, message, cause);
		this.tenantSlug = tenantSlug;
	}

	public String tenantSlug() {
		return tenantSlug;
	}
}
