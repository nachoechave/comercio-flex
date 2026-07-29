package com.comercioflex.catalog.api;

import java.util.List;
import java.util.UUID;

import com.comercioflex.catalog.application.CreateProductCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
	@NotBlank
	@Size(max = 160)
	String name,

	@Size(max = 2000)
	String description,

	@NotNull
	UUID categoryId,

	@NotNull
	@Size(min = 1, max = 100)
	List<@Valid ProductVariantRequest> variants) {

	CreateProductCommand toCommand() {
		return new CreateProductCommand(
			name,
			description,
			categoryId,
			variants.stream().map(ProductVariantRequest::toValues).toList());
	}
}
