package com.comercioflex.platformadmin.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.comercioflex.media.application.InvalidProductImageException;
import com.comercioflex.platformadmin.application.CompanyNotFoundException;
import com.comercioflex.platformadmin.application.CompanyCreationConflictException;
import com.comercioflex.platformadmin.application.CompanyProvisioningException;
import com.comercioflex.platformadmin.application.CompanyProvisioningUnavailableException;
import com.comercioflex.platformadmin.application.CompanyStatusConflictException;
import com.comercioflex.platformadmin.application.CompanyUpdateConflictException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(basePackageClasses = SuperAdminController.class)
public class SuperAdminErrorHandler {

	@ExceptionHandler(CompanyNotFoundException.class)
	ProblemDetail notFound(HttpServletRequest request) {
		return problem(
			HttpStatus.NOT_FOUND,
			"Empresa no encontrada",
			"No existe una empresa para el identificador solicitado.",
			"company-not-found",
			request);
	}

	@ExceptionHandler(CompanyStatusConflictException.class)
	ProblemDetail conflict(
			CompanyStatusConflictException exception,
			HttpServletRequest request) {
		return problem(
			HttpStatus.CONFLICT,
			"Cambio de estado no permitido",
			exception.getMessage(),
			"company-status-conflict",
			request);
	}

	@ExceptionHandler(CompanyCreationConflictException.class)
	ProblemDetail creationConflict(
			CompanyCreationConflictException exception,
			HttpServletRequest request) {
		return problem(
			HttpStatus.CONFLICT,
			"No se pudo registrar la empresa",
			exception.getMessage(),
			"company-creation-conflict",
			request);
	}

	@ExceptionHandler(CompanyUpdateConflictException.class)
	ProblemDetail updateConflict(
			CompanyUpdateConflictException exception,
			HttpServletRequest request) {
		return problem(
			HttpStatus.CONFLICT,
			"No se pudo actualizar la empresa",
			exception.getMessage(),
			"company-update-conflict",
			request);
	}

	@ExceptionHandler({
		CompanyProvisioningException.class,
		CompanyProvisioningUnavailableException.class
	})
	ProblemDetail provisioningUnavailable(
			RuntimeException exception,
			HttpServletRequest request) {
		return problem(
			HttpStatus.SERVICE_UNAVAILABLE,
			"Aprovisionamiento no disponible",
			exception.getMessage(),
			"company-provisioning-unavailable",
			request);
	}

	@ExceptionHandler({InvalidProductImageException.class, MaxUploadSizeExceededException.class})
	ProblemDetail invalidImage(RuntimeException exception, HttpServletRequest request) {
		String detail = exception instanceof InvalidProductImageException
			? exception.getMessage() : "La imagen no puede superar 5 MB.";
		return problem(
			HttpStatus.BAD_REQUEST,
			"Imagen inválida",
			detail,
			"invalid-branding-image",
			request);
	}

	private ProblemDetail problem(
			HttpStatus status,
			String title,
			String detail,
			String type,
			HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("https://comercio-flex.local/problems/" + type));
		problem.setInstance(URI.create(request.getRequestURI()));
		return problem;
	}
}
