package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.application.ProductPage;

public record ProductPageResponse(
	List<ProductSummaryResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static ProductPageResponse from(ProductPage page) {
		return new ProductPageResponse(
			page.items().stream().map(ProductSummaryResponse::from).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			page.totalPages());
	}
}
