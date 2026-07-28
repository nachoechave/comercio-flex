package com.comercioflex.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
	@NotBlank
	@Size(max = 120)
	String name) {
}
