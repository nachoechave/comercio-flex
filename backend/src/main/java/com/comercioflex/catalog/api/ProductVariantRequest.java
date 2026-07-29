package com.comercioflex.catalog.api;

import com.comercioflex.catalog.application.RawVariantValues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductVariantRequest(
	@NotBlank
	@Size(max = 64)
	String sku,

	@NotBlank
	@Size(max = 32)
	String price,

	@Size(max = 60)
	String size,

	@Size(max = 60)
	String color) {

	RawVariantValues toValues() {
		return new RawVariantValues(sku, price, size, color);
	}
}
