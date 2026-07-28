package com.comercioflex.identity.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.comercioflex.identity.application.InvalidCredentialsException;
import com.comercioflex.identity.application.LoginRateLimitExceededException;
import com.comercioflex.identity.application.TenantAccessDeniedException;

@RestControllerAdvice
public class IdentityErrorHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail invalidCredentials() {
		return problem(
			HttpStatus.UNAUTHORIZED,
			"Credenciales inválidas",
			"El correo o la contraseña no son válidos.",
			"invalid-credentials");
	}

	@ExceptionHandler(LoginRateLimitExceededException.class)
	ProblemDetail rateLimited() {
		return problem(
			HttpStatus.TOO_MANY_REQUESTS,
			"Demasiados intentos",
			"Esperá unos minutos antes de volver a intentar.",
			"login-rate-limited");
	}

	@ExceptionHandler(TenantAccessDeniedException.class)
	ProblemDetail tenantAccessDenied() {
		return problem(
			HttpStatus.FORBIDDEN,
			"Acceso denegado",
			"No tenés acceso administrativo a este comercio.",
			"tenant-access-denied");
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
