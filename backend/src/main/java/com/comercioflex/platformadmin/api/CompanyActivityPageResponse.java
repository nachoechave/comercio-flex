package com.comercioflex.platformadmin.api;

import java.util.List;

import com.comercioflex.platformadmin.application.CompanyActivityPage;

public record CompanyActivityPageResponse(
	List<CompanyActivityResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static CompanyActivityPageResponse from(CompanyActivityPage page) {
		return new CompanyActivityPageResponse(
			page.items().stream().map(CompanyActivityResponse::from).toList(),
			page.page(), page.size(), page.totalItems(), page.totalPages());
	}
}
