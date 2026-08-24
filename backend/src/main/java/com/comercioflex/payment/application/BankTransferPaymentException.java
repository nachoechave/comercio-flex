package com.comercioflex.payment.application;

public class BankTransferPaymentException extends RuntimeException {
	private final String code;

	public BankTransferPaymentException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
