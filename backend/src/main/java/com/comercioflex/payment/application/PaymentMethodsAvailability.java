package com.comercioflex.payment.application;

public record PaymentMethodsAvailability(
	boolean mercadoPago,
	boolean bankTransfer
) {
}
