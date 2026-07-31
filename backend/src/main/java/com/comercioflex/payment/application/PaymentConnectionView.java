package com.comercioflex.payment.application;

import java.time.Instant;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record PaymentConnectionView(
	String provider,
	PaymentEnvironment environment,
	String status,
	String connectedAccountLabel,
	Instant connectedAt) {
}
