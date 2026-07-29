package com.comercioflex.catalog.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeVariantStatusRequest(
	@NotNull
	Boolean active,

	@NotNull
	@PositiveOrZero
	Long version) {
}
