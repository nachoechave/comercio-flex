package com.comercioflex.payment.application;

public class InvalidPaymentException extends RuntimeException {

	public InvalidPaymentException(String message) {
		super(message);
	}
}
