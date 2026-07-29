package com.comercioflex.catalog.application;

public record PublicCatalogSearch(
	int page,
	int size,
	String query,
	String categorySlug) {
}
