package com.comercioflex.tenant.api;

import com.comercioflex.tenant.domain.StoreSettings;
import java.math.BigDecimal;

public record AdminStoreSettingsResponse(
	String slug,
	String storeName,
	String currencyCode,
	String timezone,
	String contactPhone,
	String contactEmail,
	String pickupAddress,
	String pickupInstructions,
	boolean bankTransferEnabled,
	BigDecimal bankTransferDiscountPercentage,
	String brandTheme,
	StoreSettingsResponse.BrandingResponse branding,
	String bankName,
	String bankAccountHolder,
	String bankAlias,
	String bankCbuCvu
) {
	static AdminStoreSettingsResponse from(String slug, StoreSettings value) {
		StoreSettingsResponse publicSettings = StoreSettingsResponse.from(slug, value);
		return new AdminStoreSettingsResponse(
			publicSettings.slug(), publicSettings.storeName(), publicSettings.currencyCode(),
			publicSettings.timezone(), publicSettings.contactPhone(), publicSettings.contactEmail(),
			publicSettings.pickupAddress(), publicSettings.pickupInstructions(),
			publicSettings.bankTransferEnabled(), value.bankTransferDiscountPercentage(), publicSettings.brandTheme(), publicSettings.branding(),
			value.bankName(),
			value.bankAccountHolder(), value.bankAlias(), value.bankCbuCvu());
	}
}
