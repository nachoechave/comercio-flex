package com.comercioflex.payment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantMembership;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.payment.application.FailedWebhookEvent;
import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.PaymentWebhookOperationsService;
import com.comercioflex.payment.application.PaymentWebhookOperationsService.WebhookRetryScheduled;
import com.comercioflex.tenant.api.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/payment-webhooks")
public class PaymentWebhookOperationsController {

	private final PaymentWebhookOperationsService service;
	private final TenantPermissionGuard permissionGuard;

	public PaymentWebhookOperationsController(
			PaymentWebhookOperationsService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	List<FailedWebhookEvent> failed(
			@PathVariable String storeSlug,
			@RequestParam(defaultValue = "DEAD") String status,
			HttpServletRequest request) {
		requireOwner(request);
		if (!"DEAD".equals(status)) {
			throw new CheckoutPaymentException(
				"INVALID_WEBHOOK_STATUS", "El filtro de estado no es valido.");
		}
		return service.listFailed(membership(request).tenantId());
	}

	@PostMapping("/{eventId}/retry")
	WebhookRetryScheduled retry(
			@PathVariable String storeSlug,
			@PathVariable UUID eventId,
			HttpServletRequest request,
			Authentication authentication) {
		requireOwner(request);
		return service.retry(
			membership(request).tenantId(), eventId,
			(PlatformPrincipal) authentication.getPrincipal());
	}

	private void requireOwner(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_PAYMENTS);
	}

	private TenantMembership membership(HttpServletRequest request) {
		return (TenantMembership) request.getAttribute(
			TenantResolutionFilter.TENANT_MEMBERSHIP_ATTRIBUTE);
	}
}
