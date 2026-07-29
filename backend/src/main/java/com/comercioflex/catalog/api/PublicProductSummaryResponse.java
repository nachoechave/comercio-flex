package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.PublicProductSummary;

public record PublicProductSummaryResponse(
	String id,
	String name,
	String slug,
	PublicCategoryResponse category,
	String priceFrom,
	String priceTo,
	boolean available) {

	static PublicProductSummaryResponse from(PublicProductSummary product) {
		return new PublicProductSummaryResponse(
			product.id().toString(),
			product.name(),
			product.slug(),
			PublicCategoryResponse.from(product.category()),
			product.priceFrom().setScale(2).toPlainString(),
			product.priceTo().setScale(2).toPlainString(),
			product.available());
	}
}
