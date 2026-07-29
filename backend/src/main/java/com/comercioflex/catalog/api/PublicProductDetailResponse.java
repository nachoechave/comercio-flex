package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.domain.PublicProductDetail;

public record PublicProductDetailResponse(
	String id,
	String name,
	String slug,
	String description,
	PublicCategoryResponse category,
	List<PublicVariantResponse> variants) {

	static PublicProductDetailResponse from(PublicProductDetail product) {
		return new PublicProductDetailResponse(
			product.id().toString(),
			product.name(),
			product.slug(),
			product.description(),
			PublicCategoryResponse.from(product.category()),
			product.variants().stream().map(PublicVariantResponse::from).toList());
	}
}
