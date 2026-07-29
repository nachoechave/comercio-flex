package com.comercioflex.catalog.application;

import java.util.List;

import com.comercioflex.catalog.domain.ProductSummary;

public record ProductPage(
	List<ProductSummary> items,
	int page,
	int size,
	long totalItems) {

	public long totalPages() {
		return totalItems == 0 ? 0 : (totalItems + size - 1) / size;
	}
}
