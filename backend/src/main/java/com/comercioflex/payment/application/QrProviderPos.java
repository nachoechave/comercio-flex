package com.comercioflex.payment.application;

public record QrProviderPos(
	String providerId,
	String externalId,
	String providerStoreId,
	String externalStoreId,
	String sellerAccountId,
	String status,
	String operatingMode) {
}
