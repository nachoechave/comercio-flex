package com.comercioflex.tenant.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.infrastructure.control.TenantDomainRepository;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/storefront-link")
public class AdminStorefrontLinkController {

	private final TenantDomainRepository domainRepository;
	private final TenantPermissionGuard permissionGuard;

	public AdminStorefrontLinkController(
			TenantDomainRepository domainRepository,
			TenantPermissionGuard permissionGuard) {
		this.domainRepository = domainRepository;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	StorefrontLinkResponse find(@PathVariable String storeSlug, HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_DASHBOARD);
		Object resolved = request.getAttribute(TenantResolutionFilter.RESOLVED_TENANT_ATTRIBUTE);
		if (!(resolved instanceof ResolvedTenant tenant)) {
			return new StorefrontLinkResponse("/tiendas/" + storeSlug, false);
		}

		return domainRepository.findVerifiedPrimaryHostnameByTenantId(tenant.id())
			.map(hostname -> new StorefrontLinkResponse("https://" + hostname, true))
			.orElseGet(() -> new StorefrontLinkResponse("/tiendas/" + storeSlug, false));
	}

	record StorefrontLinkResponse(String url, boolean customDomain) {
	}
}
