package com.comercioflex.catalog.domain;

public enum ProductStatus {
	DRAFT,
	PUBLISHED,
	ARCHIVED;

	public boolean canTransitionTo(ProductStatus target) {
		return switch (this) {
			case DRAFT -> target == DRAFT
				|| target == PUBLISHED
				|| target == ARCHIVED;
			case PUBLISHED -> target == PUBLISHED
				|| target == DRAFT
				|| target == ARCHIVED;
			case ARCHIVED -> target == ARCHIVED || target == DRAFT;
		};
	}
}
