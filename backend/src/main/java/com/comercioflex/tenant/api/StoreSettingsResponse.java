package com.comercioflex.tenant.api;

public record StoreSettingsResponse(
	String slug,
	String storeName,
	String currencyCode,
	String timezone,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions,
	String brandTheme
) {
	static StoreSettingsResponse from(String slug, com.comercioflex.tenant.domain.StoreSettings settings) {
		return new StoreSettingsResponse(slug, settings.storeName(), settings.currencyCode(),
			settings.timezone(), settings.contactPhone(), settings.contactEmail(),
			settings.pickupAddress(), settings.pickupInstructions(), settings.brandTheme().name());
	}
}
