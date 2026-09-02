package com.comercioflex.payment.application;

public class QrOrderException extends RuntimeException {

	private final String code;
	private final boolean retryable;

	public QrOrderException(String code, String message) {
		this(code, message, false, null);
	}

	public QrOrderException(String code, String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.retryable = retryable;
	}

	public String code() {
		return code;
	}

	public boolean retryable() {
		return retryable;
	}
}
