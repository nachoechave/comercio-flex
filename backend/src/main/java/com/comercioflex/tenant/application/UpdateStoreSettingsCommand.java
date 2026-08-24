package com.comercioflex.tenant.application;

public record UpdateStoreSettingsCommand(
	String storeName,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions,
	boolean bankTransferEnabled,
	String bankName,
	String bankAccountHolder,
	String bankAlias,
	String bankCbuCvu
) {
}
