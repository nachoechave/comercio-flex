package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.VariantOptionValue;

public record VariantOptionValueResponse(
	String name,
	String value) {

	public static VariantOptionValueResponse from(VariantOptionValue option) {
		return new VariantOptionValueResponse(option.name(), option.value());
	}
}
