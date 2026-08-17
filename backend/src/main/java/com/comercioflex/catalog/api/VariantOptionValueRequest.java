package com.comercioflex.catalog.api;

import com.comercioflex.catalog.application.RawVariantOptionValue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VariantOptionValueRequest(
	@NotBlank @Size(max = 40) String name,
	@NotBlank @Size(max = 60) String value) {

	RawVariantOptionValue toValues() {
		return new RawVariantOptionValue(name, value);
	}
}
