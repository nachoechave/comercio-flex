package com.comercioflex.tenant.application;

import com.comercioflex.tenant.domain.BrandFont;
import com.comercioflex.tenant.domain.StorefrontTemplate;

public record UpdateTenantBrandingCommand(
	String primaryColor,
	String secondaryColor,
	String backgroundColor,
	String textColor,
	BrandFont font,
	String heroTitle,
	String heroSubtitle,
	StorefrontTemplate template) {
}
