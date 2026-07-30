package com.comercioflex.order.api;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateGuestOrderItemRequest(
	@NotNull UUID variantId,
	@NotBlank
	@Pattern(
		regexp = "^(?:[1-9]|[1-9][0-9])$",
		message = "debe ser un entero entre 1 y 99")
	String quantity) {

	BigDecimal decimalQuantity() {
		return new BigDecimal(quantity);
	}
}
