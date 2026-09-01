package com.comercioflex.payment.application;

public enum QrAuthorizationStatus {

	NOT_CHECKED,
	AUTHORIZED,
	UNAUTHORIZED_SCOPES,
	NOT_FOUND,
	PROVIDER_ERROR
}
