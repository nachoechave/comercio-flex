package com.comercioflex.tenant.application;

public record UpdateStoreSettingsCommand(
	String storeName,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions
) {
}
