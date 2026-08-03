package com.comercioflex.dashboard.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record UpdateDashboardSettingsRequest(
	@NotNull
	@DecimalMin("0.000")
	@DecimalMax("999999999999.999")
	@Digits(integer = 12, fraction = 3)
	BigDecimal lowStockThreshold) {
}
