package com.comercioflex.catalog.application;

import java.util.List;

import com.comercioflex.catalog.domain.PublicProductSummary;

public record PublicProductPage(
	List<PublicProductSummary> items,
	int page,
	int size,
	long totalItems) {

	public long totalPages() {
		return totalItems == 0 ? 0 : (totalItems + size - 1) / size;
	}
}
