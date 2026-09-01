package com.comercioflex.payment.application;

import java.math.BigDecimal;

public record QrStoreSetupCommand(
	String storeName,
	String streetName,
	String streetNumber,
	String cityName,
	String stateName,
	BigDecimal latitude,
	BigDecimal longitude,
	String reference) {
}
