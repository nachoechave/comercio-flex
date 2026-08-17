package com.comercioflex.platformadmin.application;

public record CompanySearch(
	int page,
	int size,
	CompanyStatusFilter status,
	String query) {
}
