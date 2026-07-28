package com.comercioflex.identity.application;

import org.springframework.stereotype.Component;

import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.tenant.api.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class TenantPermissionGuard {

	public void require(HttpServletRequest request, TenantPermission permission) {
		Object attribute = request.getAttribute(
			TenantResolutionFilter.TENANT_MEMBERSHIP_ATTRIBUTE);
		if (!(attribute instanceof TenantMembership membership)
				|| !membership.role().allows(permission)) {
			throw new TenantAccessDeniedException();
		}
	}
}
