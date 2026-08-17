package com.comercioflex.identity.application;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class PlatformRoleGuard {

	public void requireSuperAdmin(HttpServletRequest request) {
		if (!Boolean.TRUE.equals(request.getAttribute(
				PlatformRoleAuthorizationManager.SUPER_ADMIN_AUTHORIZED_ATTRIBUTE))) {
			throw new TenantAccessDeniedException();
		}
	}
}
