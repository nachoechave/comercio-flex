package com.comercioflex.payment.application;

public final class QrSetupException extends RuntimeException {

	private final String code;

	public QrSetupException(String code, String message) {
		super(message);
		this.code = code;
	}

	public QrSetupException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
