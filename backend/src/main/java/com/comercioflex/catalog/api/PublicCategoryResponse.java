package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.PublicCategory;

public record PublicCategoryResponse(String id, String name, String slug) {

	static PublicCategoryResponse from(PublicCategory category) {
		return new PublicCategoryResponse(
			category.id().toString(),
			category.name(),
			category.slug());
	}
}
