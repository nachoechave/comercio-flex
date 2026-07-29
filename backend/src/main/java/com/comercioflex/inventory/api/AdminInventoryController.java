package com.comercioflex.inventory.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.comercioflex.inventory.application.AdjustmentCommand;
import com.comercioflex.inventory.application.AdjustmentResult;
import com.comercioflex.inventory.application.InventorySearch;
import com.comercioflex.inventory.application.InventoryService;
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.inventory.domain.InventoryAvailability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/inventory")
public class AdminInventoryController {

	private final InventoryService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminInventoryController(
			InventoryService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	InventoryPageResponse findAll(
			@RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "ALL") InventoryAvailability availability,
			HttpServletRequest request) {
		require(request, TenantPermission.VIEW_INVENTORY);
		return InventoryPageResponse.from(service.findPage(
			new InventorySearch(page, size, q, availability)));
	}

	@GetMapping("/variants/{variantId}")
	InventoryItemResponse findVariant(
			@PathVariable UUID variantId,
			HttpServletRequest request) {
		require(request, TenantPermission.VIEW_INVENTORY);
		return InventoryItemResponse.from(service.findItem(variantId));
	}

	@GetMapping("/variants/{variantId}/movements")
	MovementPageResponse findMovements(
			@PathVariable UUID variantId,
			@RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			HttpServletRequest request) {
		require(request, TenantPermission.VIEW_INVENTORY);
		return MovementPageResponse.from(service.findMovements(variantId, page, size));
	}

	@PostMapping("/variants/{variantId}/adjustments")
	ResponseEntity<AdjustmentResponse> adjust(
			@PathVariable UUID variantId,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody AdjustmentRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request, TenantPermission.ADJUST_STOCK);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		AdjustmentResult result = service.adjust(new AdjustmentCommand(
			variantId,
			idempotencyKey,
			body.direction(),
			body.decimalQuantity(),
			body.reason(),
			body.note(),
			new InventoryActor(principal.publicId(), principal.displayName())));
		HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(AdjustmentResponse.from(result));
	}

	private void require(HttpServletRequest request, TenantPermission permission) {
		permissionGuard.require(request, permission);
	}
}
