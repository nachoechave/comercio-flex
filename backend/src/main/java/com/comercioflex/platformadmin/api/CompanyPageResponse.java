package com.comercioflex.platformadmin.api;

import java.util.List;

import com.comercioflex.platformadmin.application.CompanyPage;

public record CompanyPageResponse(
	List<CompanySummaryResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static CompanyPageResponse from(CompanyPage page) {
		return new CompanyPageResponse(
			page.items().stream().map(CompanySummaryResponse::from).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			page.totalPages());
	}
}
