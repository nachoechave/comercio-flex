package com.comercioflex.payment.application;

public class PaymentOAuthException extends RuntimeException {

	private final String code;

	public PaymentOAuthException(String code, String message) {
		super(message);
		this.code = code;
	}

	public PaymentOAuthException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
