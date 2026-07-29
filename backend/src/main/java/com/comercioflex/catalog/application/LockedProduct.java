package com.comercioflex.catalog.application;

import com.comercioflex.catalog.domain.ProductStatus;

public record LockedProduct(
	long internalId,
	long categoryInternalId,
	ProductStatus status,
	long version) {
}
