package com.comercioflex.payment.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantMembership;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.payment.application.MerchantPaymentConnectionService;
import com.comercioflex.payment.application.PaymentAuthorizationStart;
import com.comercioflex.payment.application.PaymentConnectionView;
import com.comercioflex.tenant.api.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/payment-connection")
public class PaymentConnectionController {

	private final MerchantPaymentConnectionService service;
	private final TenantPermissionGuard permissionGuard;

	public PaymentConnectionController(
			MerchantPaymentConnectionService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	PaymentConnectionView view(
			@PathVariable String storeSlug,
			HttpServletRequest request) {
		requireOwner(request);
		return service.view(membership(request).tenantId(), storeSlug);
	}

	@PostMapping("/authorization")
	PaymentAuthorizationStart authorize(
			@PathVariable String storeSlug,
			HttpServletRequest request,
			Authentication authentication) {
		requireOwner(request);
		return service.start(
			membership(request).tenantId(), storeSlug, principal(authentication));
	}

	@DeleteMapping
	ResponseEntity<Void> disconnect(
			@PathVariable String storeSlug,
			HttpServletRequest request,
			Authentication authentication) {
		requireOwner(request);
		service.disconnect(
			membership(request).tenantId(), storeSlug, principal(authentication));
		return ResponseEntity.noContent().build();
	}

	private void requireOwner(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_PAYMENTS);
	}

	private TenantMembership membership(HttpServletRequest request) {
		return (TenantMembership) request.getAttribute(
			TenantResolutionFilter.TENANT_MEMBERSHIP_ATTRIBUTE);
	}

	private PlatformPrincipal principal(Authentication authentication) {
		return (PlatformPrincipal) authentication.getPrincipal();
	}
}
