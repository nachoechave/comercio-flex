package com.comercioflex.identity.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import com.comercioflex.identity.application.*;
import com.comercioflex.identity.infrastructure.IdentityRecoveryDelivery;
import com.comercioflex.tenant.api.TenantResolutionFilter;
import com.comercioflex.tenant.application.ResolvedTenant;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}")
public class PublicIdentityController {

	private final PublicIdentityService service;
	private final PublicIdentityRateLimiter limiter;
	private final IdentityRecoveryDelivery recovery;
	private final EmailNormalizer emails;

	public PublicIdentityController(PublicIdentityService service, PublicIdentityRateLimiter limiter,
			IdentityRecoveryDelivery recovery, EmailNormalizer emails) {
		this.service = service; this.limiter = limiter; this.recovery = recovery; this.emails = emails;
	}

	@PostMapping("/member-registration")
	@ResponseStatus(HttpStatus.ACCEPTED)
	Map<String, String> register(@Valid @RequestBody Registration body, HttpServletRequest request) {
		radio(request);
		limiter.acquire("register", request.getRemoteAddr(), emails.normalize(body.email()));
		service.register(body.firstName(), body.lastName(), body.email(), body.phone(), body.password());
		return Map.of("message", "Solicitud recibida. Podés iniciar sesión o recuperar tu contraseña si ya tenías cuenta.");
	}

	@GetMapping("/me/profile")
	PublicProfile profile(@AuthenticationPrincipal PlatformPrincipal principal, HttpServletRequest request) {
		radio(request);
		return service.profile(principal.id());
	}

	@PutMapping("/me/profile")
	PublicProfile update(@AuthenticationPrincipal PlatformPrincipal principal,
			@Valid @RequestBody ProfileUpdate body, HttpServletRequest request) {
		radio(request);
		return service.update(principal.id(), body.firstName(), body.lastName(), body.phone());
	}

	@PostMapping("/account/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	Map<String, String> forgot(@Valid @RequestBody Forgot body, HttpServletRequest request) {
		ResolvedTenant tenant = radio(request);
		String email = emails.normalize(body.email());
		limiter.acquire("forgot", request.getRemoteAddr(), email);
		recovery.request(tenant.slug(), email);
		return Map.of("message", "Si existe una cuenta con ese correo, te enviamos instrucciones.");
	}

	@PostMapping("/account/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reset(@Valid @RequestBody Reset body, HttpServletRequest request) {
		ResolvedTenant tenant = radio(request);
		limiter.acquire("reset", request.getRemoteAddr(), request.getRemoteAddr());
		service.reset(tenant.id(), body.token(), body.password());
	}

	@ExceptionHandler(PublicIdentityException.class)
	ProblemDetail invalid(PublicIdentityException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	// Default MVC validation logging may include rejected password/token values.
	@ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
		org.springframework.http.converter.HttpMessageNotReadableException.class})
	ProblemDetail invalidRequest() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisá los campos de la solicitud.");
	}

	private ResolvedTenant radio(HttpServletRequest request) {
		ResolvedTenant tenant = (ResolvedTenant) request.getAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE);
		PublicIdentityService.requireRadio(tenant);
		return tenant;
	}

	public record Registration(@NotBlank @Size(max = 70) String firstName,
		@NotBlank @Size(max = 70) String lastName, @NotBlank @Email @Size(max = 254) String email,
		@Size(max = 40) String phone, @NotBlank @Size(min = 12, max = 72) String password) {
		@com.fasterxml.jackson.annotation.JsonAnySetter
		public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Campo no permitido."); }
		@Override public String toString() { return "Registration[redacted]"; }
	}
	public record ProfileUpdate(@NotBlank @Size(max = 70) String firstName,
		@NotBlank @Size(max = 70) String lastName, @Size(max = 40) String phone) {
		@com.fasterxml.jackson.annotation.JsonAnySetter
		public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Campo no permitido."); }
	}
	public record Forgot(@NotBlank @Email @Size(max = 254) String email) {}
	public record Reset(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
		@NotBlank @Size(min = 12, max = 72) String password) {
		@Override public String toString() { return "Reset[redacted]"; }
	}
}
