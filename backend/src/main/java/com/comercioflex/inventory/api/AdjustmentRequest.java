package com.comercioflex.inventory.api;

import java.math.BigDecimal;

import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdjustmentRequest(
	@NotNull AdjustmentDirection direction,
	@NotBlank
	@Pattern(
		regexp = "^[0-9]{1,12}(?:\\.[0-9]{1,3})?$",
		message = "debe ser una cantidad decimal positiva con hasta tres decimales")
	String quantity,
	@NotNull InventoryReason reason,
	@Size(max = 500) String note) {

	BigDecimal decimalQuantity() {
		return new BigDecimal(quantity);
	}
}
