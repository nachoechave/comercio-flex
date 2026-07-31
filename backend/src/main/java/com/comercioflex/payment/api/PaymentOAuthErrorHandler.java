package com.comercioflex.payment.api;

import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.payment.application.PaymentOAuthException;

@RestControllerAdvice(basePackageClasses = PaymentConnectionController.class)
public class PaymentOAuthErrorHandler {

	private static final Set<String> CONFLICTS = Set.of(
		"SELLER_ALREADY_CONNECTED", "DISCONNECT_REQUIRED", "CONNECTION_CHANGED");

	@ExceptionHandler(PaymentOAuthException.class)
	ProblemDetail paymentOAuth(PaymentOAuthException exception) {
		HttpStatus status = switch (exception.code()) {
			case "PAYMENT_CONNECTION_DISABLED", "OAUTH_PROVIDER_UNAVAILABLE",
				"SELLER_PROFILE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			default -> CONFLICTS.contains(exception.code())
				? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
		};
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
		problem.setTitle("No se pudo configurar Mercado Pago");
		problem.setType(URI.create(
			"https://comercio-flex.local/problems/payment-connection"));
		problem.setProperty("code", exception.code());
		return problem;
	}
}
