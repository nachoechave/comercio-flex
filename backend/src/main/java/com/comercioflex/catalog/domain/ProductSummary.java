package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImageReference;

public record ProductSummary(
	UUID id,
	String name,
	String slug,
	ProductStatus status,
	ProductCategory category,
	ProductImageReference image,
	long variantCount,
	long activeVariantCount,
	BigDecimal priceFrom,
	BigDecimal priceTo,
	long version,
	Instant updatedAt) {
}
