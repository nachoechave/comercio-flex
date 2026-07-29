package com.comercioflex.catalog.api;

import java.time.Instant;

import com.comercioflex.catalog.domain.ProductVariant;

public record ProductVariantResponse(
	String id,
	String sku,
	String price,
	String size,
	String color,
	boolean active,
	long version,
	Instant createdAt,
	Instant updatedAt) {

	static ProductVariantResponse from(ProductVariant variant) {
		return new ProductVariantResponse(
			variant.id().toString(),
			variant.sku(),
			variant.price().setScale(2).toPlainString(),
			variant.size(),
			variant.color(),
			variant.active(),
			variant.version(),
			variant.createdAt(),
			variant.updatedAt());
	}
}
