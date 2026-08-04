package com.comercioflex.tenant.domain;

public record StoreSettings(
	String storeName,
	String currencyCode,
	String timezone,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions,
	BrandTheme brandTheme
) {
}
