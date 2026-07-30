package com.comercioflex.order.api;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.order.application.AdminOrderSearch;
import com.comercioflex.order.application.AdminOrderService;
import com.comercioflex.order.application.OrderTransitionCommand;
import com.comercioflex.order.domain.OrderStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/orders")
public class AdminOrderController {

	private final AdminOrderService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminOrderController(
			AdminOrderService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	AdminOrderPageResponse findAll(
			@RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) OrderStatus status,
			HttpServletRequest request) {
		require(request);
		return AdminOrderPageResponse.from(
			service.findPage(new AdminOrderSearch(page, size, q, status)));
	}

	@GetMapping("/{orderId}")
	AdminOrderDetailResponse find(
			@PathVariable UUID orderId,
			HttpServletRequest request) {
		require(request);
		return AdminOrderDetailResponse.from(service.find(orderId));
	}

	@PostMapping("/{orderId}/transitions")
	AdminOrderDetailResponse transition(
			@PathVariable UUID orderId,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody TransitionOrderRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		return AdminOrderDetailResponse.from(service.transition(new OrderTransitionCommand(
			orderId,
			idempotencyKey,
			body.targetStatus(),
			body.note(),
			principal.publicId(),
			principal.displayName())));
	}

	private void require(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_ORDERS);
	}
}
