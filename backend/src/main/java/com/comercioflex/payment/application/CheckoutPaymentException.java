package com.comercioflex.payment.application;

public class CheckoutPaymentException extends RuntimeException {

	private final String code;

	public CheckoutPaymentException(String code, String message) {
		super(message);
		this.code = code;
	}

	public CheckoutPaymentException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
