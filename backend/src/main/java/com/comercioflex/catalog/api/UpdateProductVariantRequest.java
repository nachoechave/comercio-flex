package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.application.RawVariantValues;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateProductVariantRequest(
	@NotBlank
	@Size(max = 64)
	String sku,

	@NotBlank
	@Size(max = 32)
	String price,

	@Size(max = 60)
	String size,

	@Size(max = 60)
	String color,

	@Size(max = 5)
	List<@Valid VariantOptionValueRequest> options,

	@NotNull
	@PositiveOrZero
	Long version) {

	RawVariantValues toValues() {
		return new RawVariantValues(
			sku, price, size, color,
			options == null ? List.of() : options.stream().map(VariantOptionValueRequest::toValues).toList());
	}
}
