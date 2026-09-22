package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.domain.PublicProductDetail;
import com.comercioflex.media.api.ProductImageResponse;

public record PublicProductDetailResponse(
	String id,
	String name,
	String slug,
	String description,
	PublicCategoryResponse category,
	ProductImageResponse image,
	List<PublicVariantResponse> variants, List<ProductImageResponse> images, String imageUrl) {

	static PublicProductDetailResponse from(PublicProductDetail product, String storeSlug) {
		return new PublicProductDetailResponse(
			product.id().toString(),
			product.name(),
			product.slug(),
			product.description(),
			PublicCategoryResponse.from(product.category()),
			product.image() == null ? null : ProductImageResponse.publicView(storeSlug, product.image()),
			product.variants().stream().map(PublicVariantResponse::from).toList(),
			product.images().stream().map(image -> ProductImageResponse.publicView(storeSlug, image)).toList(),
			product.image() == null ? null : ProductImageResponse.publicView(storeSlug, product.image()).url());
	}
}
