package com.comercioflex.payment.api;

import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.payment.application.CheckoutPaymentException;

@RestControllerAdvice(assignableTypes = {
	CheckoutProController.class,
	MercadoPagoWebhookController.class
})
public class CheckoutPaymentErrorHandler {

	private static final Set<String> CONFLICTS = Set.of(
		"PAYMENTS_NOT_ENABLED", "ORDER_NOT_PAYABLE", "PAYMENT_ALREADY_IN_PROGRESS",
		"IDEMPOTENCY_CONFLICT", "PAYMENT_REQUIRES_REVIEW",
		"PAYMENT_CONCURRENT_UPDATE", "PROVIDER_PAYMENT_CONFLICT");

	@ExceptionHandler(CheckoutPaymentException.class)
	ProblemDetail checkout(CheckoutPaymentException exception) {
		HttpStatus status = status(exception.code());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
		problem.setTitle("No se pudo procesar el pago");
		problem.setType(URI.create("https://comercio-flex/errors/payment"));
		problem.setProperty("code", exception.code());
		return problem;
	}

	private HttpStatus status(String code) {
		if (code.equals("PAYMENT_NOT_FOUND") || code.equals("INVALID_WEBHOOK_ROUTE")) {
			return HttpStatus.NOT_FOUND;
		}
		if (code.equals("INVALID_WEBHOOK_SIGNATURE")
				|| code.equals("EXPIRED_WEBHOOK_SIGNATURE")) {
			return HttpStatus.UNAUTHORIZED;
		}
		if (CONFLICTS.contains(code) || code.endsWith("MISMATCH")) {
			return HttpStatus.CONFLICT;
		}
		if (code.endsWith("FAILED") && !code.startsWith("INVALID")) {
			return HttpStatus.BAD_GATEWAY;
		}
		return HttpStatus.BAD_REQUEST;
	}
}
