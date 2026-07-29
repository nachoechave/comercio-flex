package com.comercioflex.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Product(
	UUID id,
	String name,
	String slug,
	String description,
	ProductStatus status,
	ProductCategory category,
	List<ProductVariant> variants,
	long version,
	Instant createdAt,
	Instant updatedAt) {
}
