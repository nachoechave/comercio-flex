package com.comercioflex.payment.application;

import com.comercioflex.payment.domain.PaymentIntent;

public record PaymentInitiation(
	PaymentIntent paymentIntent,
	boolean replayed) {
}
