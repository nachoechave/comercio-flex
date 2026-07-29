package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductSummary(
	UUID id,
	String name,
	String slug,
	ProductStatus status,
	ProductCategory category,
	long variantCount,
	long activeVariantCount,
	BigDecimal priceFrom,
	BigDecimal priceTo,
	long version,
	Instant updatedAt) {
}
