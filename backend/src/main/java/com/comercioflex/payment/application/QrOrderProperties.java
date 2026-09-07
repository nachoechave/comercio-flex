package com.comercioflex.payment.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payments.qr-orders")
public record QrOrderProperties(
	String webhookSecret,
	Duration signatureTolerance) {

	public QrOrderProperties {
		signatureTolerance = signatureTolerance == null
			? Duration.ofMinutes(5) : signatureTolerance;
		if (signatureTolerance.isZero() || signatureTolerance.isNegative()) {
			throw new IllegalArgumentException(
				"La tolerancia de firma QR debe ser positiva.");
		}
	}
}
