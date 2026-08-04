package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.comercioflex.media.domain.ProductImageReference;

public record PublicProductSummary(
	UUID id,
	String name,
	String slug,
	PublicCategory category,
	ProductImageReference image,
	BigDecimal priceFrom,
	BigDecimal priceTo,
	boolean available) {
}
