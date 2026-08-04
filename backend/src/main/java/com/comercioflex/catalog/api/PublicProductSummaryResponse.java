package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.PublicProductSummary;
import com.comercioflex.media.api.ProductImageResponse;

public record PublicProductSummaryResponse(
	String id,
	String name,
	String slug,
	PublicCategoryResponse category,
	ProductImageResponse image,
	String priceFrom,
	String priceTo,
	boolean available) {

	static PublicProductSummaryResponse from(PublicProductSummary product, String storeSlug) {
		return new PublicProductSummaryResponse(
			product.id().toString(),
			product.name(),
			product.slug(),
			PublicCategoryResponse.from(product.category()),
			product.image() == null ? null : ProductImageResponse.publicView(storeSlug, product.image()),
			product.priceFrom().setScale(2).toPlainString(),
			product.priceTo().setScale(2).toPlainString(),
			product.available());
	}
}
