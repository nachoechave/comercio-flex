package com.comercioflex.platformadmin.api;

import com.comercioflex.platformadmin.application.CompanyBranding;
import com.comercioflex.tenant.domain.BrandAssetReference;

public record CompanyBrandingResponse(
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

	static CompanyBrandingResponse from(CompanyBranding company) {
		var branding = company.branding();
		return new CompanyBrandingResponse(
			branding.primaryColor(), branding.secondaryColor(), branding.backgroundColor(),
			branding.textColor(), branding.font().name(), branding.heroTitle(),
			branding.heroSubtitle(), branding.template().name(),
			url(company.slug(), "logo", branding.logo()),
			url(company.slug(), "favicon", branding.favicon()),
			url(company.slug(), "hero", branding.hero()));
	}

	private static String url(String slug, String type, BrandAssetReference asset) {
		return asset == null ? null : "/api/v1/stores/" + slug
			+ "/media/branding/" + type + "?v=" + asset.etag();
	}
}
