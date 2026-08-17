package com.comercioflex.tenant.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class UpdateStoreSettingsRequestTests {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void acceptsACompleteConfiguration() {
		var request = new UpdateStoreSettingsRequest("Mi tienda", "+54 11 4444-5555",
			"ventas@mitienda.test", "Calle 123", "Tocar timbre");
		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void rejectsMissingContactAndValuesThatOnlyMeetLengthBeforeTrimming() {
		var request = new UpdateStoreSettingsRequest(" A ", "", "", " x ", "");
		assertThat(validator.validate(request)).isNotEmpty();
	}

	@Test
	void rejectsMalformedPhoneAndEmail() {
		var request = new UpdateStoreSettingsRequest("Mi tienda", "abc1234", "correo-invalido",
			"Calle 123", "");
		assertThat(validator.validate(request)).hasSizeGreaterThanOrEqualTo(2);
	}
}
