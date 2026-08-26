package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.PublicCategory;
import com.comercioflex.media.api.ProductImageResponse;

public record PublicCategoryCardResponse(
	String id,
	String name,
	String slug,
	ProductImageResponse image) {

	static PublicCategoryCardResponse from(PublicCategory category, String storeSlug) {
		return new PublicCategoryCardResponse(
			category.id().toString(),
			category.name(),
			category.slug(),
			category.image() == null
				? null
				: ProductImageResponse.publicView(storeSlug, category.image()));
	}
}
