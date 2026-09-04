package com.comercioflex.tenant.api;
import java.math.BigDecimal;

public record StoreSettingsResponse(
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
	BrandingResponse branding
) {
	static StoreSettingsResponse from(String slug, com.comercioflex.tenant.domain.StoreSettings settings) {
		return new StoreSettingsResponse(slug, settings.storeName(), settings.currencyCode(),
			settings.timezone(), settings.contactPhone(), settings.contactEmail(),
			settings.pickupAddress(), settings.pickupInstructions(),
			settings.bankTransferEnabled(), settings.bankTransferDiscountPercentage(), settings.brandTheme().name(),
			BrandingResponse.from(slug, settings.branding()));
	}

	public record BrandingResponse(
		String primaryColor,
		String secondaryColor,
		String backgroundColor,
		String textColor,
		String font,
		String heroTitle,
		String heroSubtitle,
		String template,
		String logoUrl,
		String faviconUrl,
		String heroImageUrl) {

		static BrandingResponse from(String slug, com.comercioflex.tenant.domain.TenantBranding branding) {
			return new BrandingResponse(
				branding.primaryColor(), branding.secondaryColor(), branding.backgroundColor(),
				branding.textColor(), branding.font().name(), branding.heroTitle(),
				branding.heroSubtitle(), branding.template().name(),
				url(slug, "logo", branding.logo()),
				url(slug, "favicon", branding.favicon()),
				url(slug, "hero", branding.hero()));
		}

		private static String url(
				String slug,
				String type,
				com.comercioflex.tenant.domain.BrandAssetReference asset) {
			return asset == null ? null : "/api/v1/stores/" + slug
				+ "/media/branding/" + type + "?v=" + asset.etag();
		}
	}
}
