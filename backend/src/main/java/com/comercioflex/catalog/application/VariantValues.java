package com.comercioflex.catalog.application;

import java.math.BigDecimal;

public record VariantValues(
	String sku,
	BigDecimal price,
	String size,
	String color) {
}
