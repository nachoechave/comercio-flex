package com.comercioflex.payment.api;

import java.math.BigDecimal;

import com.comercioflex.payment.application.QrStoreSetupCommand;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfigureQrRequest(
	@NotBlank @Size(max = 60) String storeName,
	@NotBlank @Size(max = 200) String streetName,
	@NotBlank @Size(max = 40) String streetNumber,
	@NotBlank @Size(max = 120) String cityName,
	@NotBlank @Size(max = 120) String stateName,
	@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
	@NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
	@Size(max = 120) String reference) {

	QrStoreSetupCommand toCommand() {
		return new QrStoreSetupCommand(
			storeName.trim(), streetName.trim(), streetNumber.trim(),
			cityName.trim(), stateName.trim(), latitude, longitude,
			reference == null || reference.isBlank() ? null : reference.trim());
	}
}
