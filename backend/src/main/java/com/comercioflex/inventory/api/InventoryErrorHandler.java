package com.comercioflex.inventory.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.inventory.application.IdempotencyConflictException;
import com.comercioflex.inventory.application.InsufficientStockException;
import com.comercioflex.inventory.application.InvalidInventoryAdjustmentException;
import com.comercioflex.inventory.application.InventoryCapacityExceededException;
import com.comercioflex.inventory.application.InventoryNotFoundException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice(basePackageClasses = AdminInventoryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryErrorHandler {

	@ExceptionHandler(InventoryNotFoundException.class)
	ProblemDetail notFound() {
		return problem(
			HttpStatus.NOT_FOUND,
			"Variante no encontrada",
			"No existe la variante solicitada.",
			"inventory-variant-not-found");
	}

	@ExceptionHandler(InsufficientStockException.class)
	ProblemDetail insufficientStock() {
		return problem(
			HttpStatus.CONFLICT,
			"Stock insuficiente",
			"El ajuste dejarÃ­a el stock por debajo de cero.",
			"insufficient-stock");
	}

	@ExceptionHandler(InventoryCapacityExceededException.class)
	ProblemDetail capacityExceeded() {
		return problem(
			HttpStatus.CONFLICT,
			"Capacidad de inventario excedida",
			"El ajuste supera la cantidad máxima representable.",
			"inventory-capacity-exceeded");
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	ProblemDetail idempotencyConflict() {
		return problem(
			HttpStatus.CONFLICT,
			"Clave de idempotencia reutilizada",
			"La clave ya fue utilizada para una solicitud diferente.",
			"idempotency-conflict");
	}

	@ExceptionHandler(InvalidInventoryAdjustmentException.class)
	ProblemDetail invalidAdjustment(InvalidInventoryAdjustmentException exception) {
		return problem(
			HttpStatus.BAD_REQUEST,
			"Ajuste invÃ¡lido",
			exception.getMessage(),
			"invalid-inventory-adjustment");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail validationFailed(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
			errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		ProblemDetail problem = problem(
			HttpStatus.BAD_REQUEST,
			"Solicitud invÃ¡lida",
			"RevisÃ¡ los campos indicados.",
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
			"Solicitud invÃ¡lida",
			"RevisÃ¡ los parÃ¡metros indicados.",
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
