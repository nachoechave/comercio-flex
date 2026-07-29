package com.comercioflex.catalog.application;

import java.util.UUID;

public record ProductSearch(
	int page,
	int size,
	ProductStatusFilter status,
	UUID categoryId,
	String query) {
}
