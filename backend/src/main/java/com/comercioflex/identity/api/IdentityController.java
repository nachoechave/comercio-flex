package com.comercioflex.identity.api;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.EmailNormalizer;
import com.comercioflex.identity.application.InvalidCredentialsException;
import com.comercioflex.identity.application.LoginAttemptLimiter;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.SessionViewService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final CsrfTokenRepository csrfTokenRepository;
	private final SessionViewService sessionViewService;
	private final EmailNormalizer emailNormalizer;
	private final LoginAttemptLimiter loginAttemptLimiter;

	public IdentityController(
			AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			CsrfTokenRepository csrfTokenRepository,
			SessionViewService sessionViewService,
			EmailNormalizer emailNormalizer,
			LoginAttemptLimiter loginAttemptLimiter) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.csrfTokenRepository = csrfTokenRepository;
		this.sessionViewService = sessionViewService;
		this.emailNormalizer = emailNormalizer;
		this.loginAttemptLimiter = loginAttemptLimiter;
	}

	@GetMapping("/csrf")
	CsrfTokenResponse csrf(CsrfToken csrfToken) {
		return new CsrfTokenResponse(
			csrfToken.getHeaderName(),
			csrfToken.getParameterName(),
			csrfToken.getToken());
	}

	@GetMapping("/session")
	SessionResponse session(Authentication authentication) {
		PlatformPrincipal principal = principal(authentication);
		return principal == null
			? SessionResponse.anonymous()
			: authenticatedResponse(principal);
	}

	@PostMapping("/login")
	SessionResponse login(
			@Valid @RequestBody LoginRequest loginRequest,
			HttpServletRequest request,
			HttpServletResponse response) {
		String email = emailNormalizer.normalize(loginRequest.email());
		String remoteAddress = request.getRemoteAddr();
		loginAttemptLimiter.checkAllowed(remoteAddress, email);

		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(
					email,
					loginRequest.password()));
		}
		catch (AuthenticationException exception) {
			loginAttemptLimiter.recordFailure(remoteAddress, email);
			throw new InvalidCredentialsException();
		}

		loginAttemptLimiter.reset(remoteAddress, email);
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		CsrfToken newToken = csrfTokenRepository.generateToken(request);
		csrfTokenRepository.saveToken(newToken, request, response);
		return authenticatedResponse((PlatformPrincipal) authentication.getPrincipal());
	}

	@PostMapping("/logout")
	void logout(
			Authentication authentication,
			HttpServletRequest request,
			HttpServletResponse response) {
		csrfTokenRepository.saveToken(null, request, response);
		new SecurityContextLogoutHandler().logout(request, response, authentication);
		new CookieClearingLogoutHandler("CFSESSION").logout(request, response, authentication);
	}

	private SessionResponse authenticatedResponse(PlatformPrincipal principal) {
		return SessionResponse.authenticated(
			principal,
			sessionViewService.membershipsFor(principal));
	}

	private PlatformPrincipal principal(Authentication authentication) {
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
			return null;
		}
		return principal;
	}
}
