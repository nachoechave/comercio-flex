package com.comercioflex.payment.api;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.application.CheckoutInitiation;

public record CheckoutInitiationResponse(
	URI checkoutUrl,
	UUID paymentAttemptId,
	Instant expiresAt,
	boolean replayed) {

	static CheckoutInitiationResponse from(CheckoutInitiation value) {
		return new CheckoutInitiationResponse(
			value.checkoutUrl(), value.paymentAttemptId(), value.expiresAt(), value.replayed());
	}
}
