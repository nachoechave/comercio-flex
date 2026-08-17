package com.comercioflex.platformadmin.application;

import java.util.List;

import com.comercioflex.platformadmin.domain.CompanySummary;

public record CompanyPage(
	List<CompanySummary> items,
	int page,
	int size,
	long totalItems) {

	public long totalPages() {
		return totalItems == 0 ? 0 : (totalItems + size - 1) / size;
	}
}
