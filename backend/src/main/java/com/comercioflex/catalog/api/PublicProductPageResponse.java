package com.comercioflex.catalog.api;

import java.util.List;

import com.comercioflex.catalog.application.PublicProductPage;

public record PublicProductPageResponse(
	List<PublicProductSummaryResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static PublicProductPageResponse from(PublicProductPage page) {
		return new PublicProductPageResponse(
			page.items().stream().map(PublicProductSummaryResponse::from).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			page.totalPages());
	}
}
