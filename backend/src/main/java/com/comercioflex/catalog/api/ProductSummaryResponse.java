package com.comercioflex.catalog.api;

import java.time.Instant;

import com.comercioflex.catalog.domain.ProductStatus;
import com.comercioflex.catalog.domain.ProductSummary;

public record ProductSummaryResponse(
	String id,
	String name,
	String slug,
	ProductStatus status,
	ProductCategoryResponse category,
	long variantCount,
	long activeVariantCount,
	String priceFrom,
	String priceTo,
	long version,
	Instant updatedAt) {

	static ProductSummaryResponse from(ProductSummary product) {
		return new ProductSummaryResponse(
			product.id().toString(),
			product.name(),
			product.slug(),
			product.status(),
			ProductCategoryResponse.from(product.category()),
			product.variantCount(),
			product.activeVariantCount(),
			product.priceFrom() == null
				? null
				: product.priceFrom().setScale(2).toPlainString(),
			product.priceTo() == null
				? null
				: product.priceTo().setScale(2).toPlainString(),
			product.version(),
			product.updatedAt());
	}
}
