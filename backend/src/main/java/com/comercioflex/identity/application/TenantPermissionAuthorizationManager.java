package com.comercioflex.identity.application;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.tenant.api.TenantResolutionFilter;

@SuppressWarnings("deprecation")
public final class TenantPermissionAuthorizationManager
		implements AuthorizationManager<RequestAuthorizationContext> {

	private final TenantPermission permission;

	public TenantPermissionAuthorizationManager(TenantPermission permission) {
		this.permission = permission;
	}

	@Override
	public AuthorizationDecision check(
			Supplier<Authentication> authentication,
			RequestAuthorizationContext context) {
		Object attribute = context.getRequest().getAttribute(
			TenantResolutionFilter.TENANT_MEMBERSHIP_ATTRIBUTE);
		boolean granted = authentication.get().isAuthenticated()
			&& attribute instanceof TenantMembership membership
			&& membership.role().allows(permission);
		return new AuthorizationDecision(granted);
	}
}
