package com.comercioflex.tenant.domain;

public record StoreSettings(
	String storeName,
	String currencyCode,
	String timezone,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions,
	boolean bankTransferEnabled,
	String bankName,
	String bankAccountHolder,
	String bankAlias,
	String bankCbuCvu,
	BrandTheme brandTheme,
	TenantBranding branding
) {
}
