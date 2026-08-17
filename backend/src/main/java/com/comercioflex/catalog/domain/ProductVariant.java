package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductVariant(
	UUID id,
	String sku,
	BigDecimal price,
	String size,
	String color,
	List<VariantOptionValue> options,
	boolean active,
	long version,
	Instant createdAt,
	Instant updatedAt) {
}
