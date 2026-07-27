package com.comercioflex.tenant.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.tenant.application.TenantNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class TenantErrorHandler {

	@ExceptionHandler(TenantNotFoundException.class)
	ProblemDetail handleTenantNotFound(
			TenantNotFoundException exception,
			HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
			HttpStatus.NOT_FOUND,
			"No existe una tienda activa para la dirección solicitada.");
		problem.setTitle("Tienda no encontrada");
		problem.setType(URI.create("https://comercio-flex.local/problems/store-not-found"));
		problem.setInstance(URI.create(request.getRequestURI()));
		return problem;
	}
}
