package com.comercioflex.tenant.api;

import com.comercioflex.tenant.application.UpdateTenantBrandingCommand;
import com.comercioflex.tenant.domain.BrandFont;
import com.comercioflex.tenant.domain.StorefrontTemplate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStoreBrandingRequest(
	@NotNull @Pattern(regexp = "#[0-9A-Fa-f]{6}") String primaryColor,
	@NotNull @Pattern(regexp = "#[0-9A-Fa-f]{6}") String secondaryColor,
	@NotNull @Pattern(regexp = "#[0-9A-Fa-f]{6}") String backgroundColor,
	@NotNull @Pattern(regexp = "#[0-9A-Fa-f]{6}") String textColor,
	@NotNull BrandFont font,
	@Size(max = 160) String heroTitle,
	@Size(max = 300) String heroSubtitle,
	@NotNull StorefrontTemplate template) {

	UpdateTenantBrandingCommand toCommand() {
		return new UpdateTenantBrandingCommand(
			primaryColor, secondaryColor, backgroundColor, textColor,
			font, heroTitle, heroSubtitle, template);
	}
}
