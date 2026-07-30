package com.comercioflex.order.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.order.application.GuestOrderNotFoundException;
import com.comercioflex.order.application.AdminOrderNotFoundException;
import com.comercioflex.order.application.InvalidGuestOrderException;
import com.comercioflex.order.application.InvalidOrderTransitionException;
import com.comercioflex.order.application.OrderIdempotencyConflictException;
import com.comercioflex.order.application.OrderTransitionIdempotencyConflictException;
import com.comercioflex.order.application.OrderUnavailableException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice(basePackageClasses = {
	GuestOrderController.class,
	AdminOrderController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GuestOrderErrorHandler {

	@ExceptionHandler(GuestOrderNotFoundException.class)
	ProblemDetail notFound() {
		return problem(
			HttpStatus.NOT_FOUND,
			"Pedido no encontrado",
			"No existe un pedido accesible con esos datos.",
			"guest-order-not-found");
	}

	@ExceptionHandler(AdminOrderNotFoundException.class)
	ProblemDetail adminNotFound() {
		return problem(
			HttpStatus.NOT_FOUND,
			"Pedido no encontrado",
			"No existe el pedido solicitado.",
			"admin-order-not-found");
	}

	@ExceptionHandler(InvalidOrderTransitionException.class)
	ProblemDetail invalidTransition(InvalidOrderTransitionException exception) {
		return problem(
			HttpStatus.CONFLICT,
			"No se pudo cambiar el pedido",
			exception.getMessage(),
			"invalid-order-transition");
	}

	@ExceptionHandler(OrderTransitionIdempotencyConflictException.class)
	ProblemDetail transitionConflict() {
		return problem(
			HttpStatus.CONFLICT,
			"Clave de idempotencia reutilizada",
			"La clave ya fue utilizada para otra transición.",
			"idempotency-conflict");
	}

	@ExceptionHandler(OrderUnavailableException.class)
	ProblemDetail unavailable() {
		return problem(
			HttpStatus.CONFLICT,
			"Producto no disponible",
			"Uno o más productos ya no están disponibles en la cantidad solicitada.",
			"order-item-unavailable");
	}

	@ExceptionHandler(OrderIdempotencyConflictException.class)
	ProblemDetail idempotencyConflict() {
		return problem(
			HttpStatus.CONFLICT,
			"Clave de idempotencia reutilizada",
			"La clave ya fue utilizada para un pedido diferente.",
			"idempotency-conflict");
	}

	@ExceptionHandler(InvalidGuestOrderException.class)
	ProblemDetail invalid(InvalidGuestOrderException exception) {
		return problem(
			HttpStatus.BAD_REQUEST,
			"Pedido inválido",
			exception.getMessage(),
			"invalid-guest-order");
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ProblemDetail missingHeader(MissingRequestHeaderException exception) {
		return problem(
			HttpStatus.BAD_REQUEST,
			"Solicitud inválida",
			"Falta el encabezado obligatorio " + exception.getHeaderName() + ".",
			"validation-failed");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail validationFailed(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
			errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		ProblemDetail problem = problem(
			HttpStatus.BAD_REQUEST,
			"Solicitud inválida",
			"Revisá los campos indicados.",
			"validation-failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail constraintViolation(ConstraintViolationException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getConstraintViolations().forEach(violation -> {
			String path = violation.getPropertyPath().toString();
			String field = path.substring(path.lastIndexOf('.') + 1);
			errors.putIfAbsent(field, violation.getMessage());
		});
		ProblemDetail problem = problem(
			HttpStatus.BAD_REQUEST,
			"Solicitud inválida",
			"Revisá los parámetros indicados.",
			"validation-failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	private ProblemDetail problem(
			HttpStatus status,
			String title,
			String detail,
			String type) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("https://comercio-flex.local/problems/" + type));
		return problem;
	}
}
