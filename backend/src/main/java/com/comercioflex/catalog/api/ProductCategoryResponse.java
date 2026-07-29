package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.ProductCategory;

public record ProductCategoryResponse(
	String id,
	String name,
	boolean active) {

	static ProductCategoryResponse from(ProductCategory category) {
		return new ProductCategoryResponse(
			category.id().toString(),
			category.name(),
			category.active());
	}
}
