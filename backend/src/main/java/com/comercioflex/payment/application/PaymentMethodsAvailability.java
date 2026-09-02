package com.comercioflex.payment.application;

public record PaymentMethodsAvailability(
	boolean mercadoPago,
	boolean mercadoPagoQr,
	boolean bankTransfer
) {
}
