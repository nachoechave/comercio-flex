package com.comercioflex.payment.application;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record QrSetupView(
	PaymentEnvironment environment,
	QrProvisioningStatus status,
	QrAuthorizationStatus authorization,
	boolean storeConfigured,
	boolean posConfigured,
	boolean externalPosIdAvailable,
	boolean qrOrdersReady) {
}
