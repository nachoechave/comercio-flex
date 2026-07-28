package com.comercioflex.catalog.api;

import java.time.Instant;

import com.comercioflex.catalog.domain.Category;

public record CategoryResponse(
	String id,
	String name,
	String slug,
	boolean active,
	Instant createdAt,
	Instant updatedAt) {

	public static CategoryResponse from(Category category) {
		return new CategoryResponse(
			category.id().toString(),
			category.name(),
			category.slug(),
			category.active(),
			category.createdAt(),
			category.updatedAt());
	}
}
