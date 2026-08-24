package com.comercioflex.payment.api;

import com.comercioflex.payment.application.PaymentMethodsAvailability;

public record PaymentMethodsResponse(
	boolean mercadoPago,
	boolean bankTransfer
) {
	static PaymentMethodsResponse from(PaymentMethodsAvailability value) {
		return new PaymentMethodsResponse(value.mercadoPago(), value.bankTransfer());
	}
}
