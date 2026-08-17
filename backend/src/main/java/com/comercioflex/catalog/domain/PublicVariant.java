package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicVariant(
	UUID id,
	BigDecimal price,
	String size,
	String color,
	List<VariantOptionValue> options,
	boolean available) {
}
