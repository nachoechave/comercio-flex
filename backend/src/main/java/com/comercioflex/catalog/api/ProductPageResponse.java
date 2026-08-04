package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.application.ProductPage;

public record ProductPageResponse(
	List<ProductSummaryResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static ProductPageResponse from(ProductPage page, String storeSlug) {
		return new ProductPageResponse(
			page.items().stream().map(item -> ProductSummaryResponse.from(item, storeSlug)).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			page.totalPages());
	}
}
