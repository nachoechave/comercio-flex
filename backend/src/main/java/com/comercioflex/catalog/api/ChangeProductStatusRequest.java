package com.comercioflex.catalog.api;

import com.comercioflex.catalog.domain.ProductStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeProductStatusRequest(
	@NotNull
	ProductStatus status,

	@NotNull
	@PositiveOrZero
	Long version) {
}
