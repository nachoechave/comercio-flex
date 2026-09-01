package com.comercioflex.payment.application;

import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record StoredQrSetup(
	long id,
	long tenantId,
	PaymentEnvironment environment,
	String providerStoreId,
	String externalStoreId,
	String providerPosId,
	String externalPosId,
	QrProvisioningStatus status,
	QrAuthorizationStatus authorization,
	UUID posIdempotencyKey,
	long version) {
}
