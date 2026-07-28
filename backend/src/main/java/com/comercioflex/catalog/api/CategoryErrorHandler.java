package com.comercioflex.catalog.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.comercioflex.catalog.application.CategoryConflictException;
import com.comercioflex.catalog.application.CategoryNotFoundException;
import com.comercioflex.catalog.application.InvalidCategoryNameException;

@RestControllerAdvice(basePackageClasses = AdminCategoryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CategoryErrorHandler {

	@ExceptionHandler(CategoryNotFoundException.class)
	ProblemDetail notFound() {
		return problem(
			HttpStatus.NOT_FOUND,
			"Categoría no encontrada",
			"No existe la categoría solicitada.",
			"category-not-found");
	}

	@ExceptionHandler(CategoryConflictException.class)
	ProblemDetail conflict() {
		return problem(
			HttpStatus.CONFLICT,
			"Categoría duplicada",
			"Ya existe una categoría con el mismo nombre o dirección.",
			"category-conflict");
	}

	@ExceptionHandler(InvalidCategoryNameException.class)
	ProblemDetail invalidName(InvalidCategoryNameException exception) {
		return problem(
			HttpStatus.BAD_REQUEST,
			"Categoría inválida",
			exception.getMessage(),
			"invalid-category");
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
