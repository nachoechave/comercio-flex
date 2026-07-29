package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicProductSummary(
	UUID id,
	String name,
	String slug,
	PublicCategory category,
	BigDecimal priceFrom,
	BigDecimal priceTo,
	boolean available) {
}
