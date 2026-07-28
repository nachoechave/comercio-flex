package com.comercioflex.catalog.api;

import jakarta.validation.constraints.NotNull;

public record ChangeCategoryStatusRequest(
	@NotNull
	Boolean active) {
}
