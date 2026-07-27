package com.comercioflex.tenant.api;

public record StoreSettingsResponse(
	String slug,
	String storeName,
	String currencyCode,
	String timezone
) {
}
