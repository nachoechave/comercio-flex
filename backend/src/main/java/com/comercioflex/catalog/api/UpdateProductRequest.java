package com.comercioflex.catalog.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
	@NotBlank
	@Size(max = 160)
	String name,

	@Size(max = 2000)
	String description,

	@NotNull
	UUID categoryId,

	@NotNull
	@PositiveOrZero
	Long version) {
}
