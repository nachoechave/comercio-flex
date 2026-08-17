package com.comercioflex.tenant.domain;

public record TenantBranding(
	String primaryColor,
	String secondaryColor,
	String backgroundColor,
	String textColor,
	BrandFont font,
	String heroTitle,
	String heroSubtitle,
	StorefrontTemplate template,
	BrandAssetReference logo,
	BrandAssetReference favicon,
	BrandAssetReference hero) {
}
