package com.comercioflex.tenant.application;

import com.comercioflex.tenant.domain.BrandTheme;

public record UpdateStoreSettingsCommand(
	String storeName,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions,
	BrandTheme brandTheme
) {
}
