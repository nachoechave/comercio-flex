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
		return new AdminStoreSettingsResponse(
			slug, value.storeName(), value.currencyCode(),
			value.timezone(), value.contactPhone(), value.contactEmail(),
			value.pickupAddress(), value.pickupInstructions(),
			value.bankTransferEnabled(), value.bankTransferDiscountPercentage(), value.brandTheme().name(), StoreSettingsResponse.BrandingResponse.from(slug, value.branding()),
			value.bankName(),
			value.bankAccountHolder(), value.bankAlias(), value.bankCbuCvu());
	}
}
