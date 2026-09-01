package com.comercioflex.payment.application;

public final class QrProviderException extends RuntimeException {

	private final QrAuthorizationStatus category;
	private final boolean recoverySearchAllowed;

	public QrProviderException(
			QrAuthorizationStatus category,
			boolean recoverySearchAllowed,
			String message,
			Throwable cause) {
		super(message, cause);
		this.category = category;
		this.recoverySearchAllowed = recoverySearchAllowed;
	}

	public QrAuthorizationStatus category() {
		return category;
	}

	public boolean recoverySearchAllowed() {
		return recoverySearchAllowed;
	}
}
