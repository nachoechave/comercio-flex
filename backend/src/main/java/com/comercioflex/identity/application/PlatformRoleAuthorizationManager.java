package com.comercioflex.identity.application;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import com.comercioflex.identity.domain.PlatformRole;

@Component
@SuppressWarnings("deprecation")
public class PlatformRoleAuthorizationManager
		implements AuthorizationManager<RequestAuthorizationContext> {

	public static final String SUPER_ADMIN_AUTHORIZED_ATTRIBUTE =
		PlatformRoleAuthorizationManager.class.getName() + ".authorized";

	private final PlatformAccessService platformAccessService;

	public PlatformRoleAuthorizationManager(PlatformAccessService platformAccessService) {
		this.platformAccessService = platformAccessService;
	}

	@Override
	public AuthorizationDecision check(
			Supplier<Authentication> authenticationSupplier,
			RequestAuthorizationContext context) {
		Authentication authentication = authenticationSupplier.get();
		boolean granted = authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof PlatformPrincipal principal
			&& platformAccessService.hasRole(principal, PlatformRole.SUPER_ADMIN);
		if (granted) {
			context.getRequest().setAttribute(SUPER_ADMIN_AUTHORIZED_ATTRIBUTE, Boolean.TRUE);
		}
		return new AuthorizationDecision(granted);
	}
}
