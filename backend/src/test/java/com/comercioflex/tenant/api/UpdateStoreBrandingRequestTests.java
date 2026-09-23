package com.comercioflex.tenant.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.comercioflex.tenant.domain.BrandFont;
import com.comercioflex.tenant.domain.StorefrontTemplate;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class UpdateStoreBrandingRequestTests {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void acceptsEachSupportedTemplate() {
		for (StorefrontTemplate template : StorefrontTemplate.values()) {
			var request = validRequest(template);
			assertThat(validator.validate(request)).isEmpty();
			assertThat(request.toCommand().template()).isEqualTo(template);
		}
	}

	@Test
	void rejectsInvalidColorsAndOversizedHeroCopy() {
		var request = new UpdateStoreBrandingRequest(
			"green", "#112233", "#FFFFFF", "#111111", BrandFont.SANS,
			"z".repeat(81), "x".repeat(161), "y".repeat(301), StorefrontTemplate.FRESH);
		assertThat(validator.validate(request)).hasSize(4);
	}

	private UpdateStoreBrandingRequest validRequest(StorefrontTemplate template) {
		return new UpdateStoreBrandingRequest(
			"#315A46", "#17352A", "#F7F5EF", "#20241F", BrandFont.SYSTEM,
			"Colección destacada", "Una tienda con identidad", "Productos elegidos para vos", template);
	}
}
