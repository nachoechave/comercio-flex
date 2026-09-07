package com.comercioflex.payment.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.payment.application.QrOrderException;

@RestControllerAdvice(assignableTypes = {
	QrOrderController.class,
	MercadoPagoQrOrderWebhookController.class
})
public class QrOrderErrorHandler {

	@ExceptionHandler(QrOrderException.class)
	ProblemDetail handle(QrOrderException exception) {
		HttpStatus status = providerStatus(exception);
		if (status == null) status = switch (exception.code()) {
			case "QR_ORDER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "INVALID_QR_WEBHOOK_SIGNATURE" -> HttpStatus.UNAUTHORIZED;
			case "QR_CREATION_IN_PROGRESS", "PAYMENT_ALREADY_IN_PROGRESS",
				"QR_IDEMPOTENCY_CONFLICT" -> HttpStatus.CONFLICT;
			case "PAYMENTS_NOT_ENABLED", "QR_SETUP_NOT_READY" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "QR_PROVIDER_UNAVAILABLE" -> HttpStatus.BAD_GATEWAY;
			default -> exception.retryable()
				? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_ENTITY;
		};
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
		detail.setType(URI.create("https://comercioflex.com.ar/problems/qr-order"));
		detail.setProperty("code", exception.code());
		return detail;
	}

	private HttpStatus providerStatus(QrOrderException exception) {
		if (!exception.code().startsWith("QR_PROVIDER_HTTP_")) return null;
		return exception.code().equals("QR_PROVIDER_HTTP_429")
			? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
	}
}
