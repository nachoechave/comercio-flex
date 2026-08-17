package com.comercioflex.catalog.application;

import java.math.BigDecimal;
import java.util.List;

import com.comercioflex.catalog.domain.VariantOptionValue;

public record VariantValues(
	String sku,
	BigDecimal price,
	String size,
	String color,
	List<VariantOptionValue> options,
	String optionSignature) {
}
