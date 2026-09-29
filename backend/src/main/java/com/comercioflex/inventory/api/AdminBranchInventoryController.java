package com.comercioflex.inventory.api;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.inventory.application.BranchInventoryService;
import com.comercioflex.inventory.application.BranchInventoryService.BranchAdjustmentView;
import com.comercioflex.inventory.application.BranchInventoryService.BranchStockView;
import com.comercioflex.inventory.application.BranchInventoryService.BranchView;
import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.inventory.domain.InventoryReason;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin")
public class AdminBranchInventoryController {

	private final BranchInventoryService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminBranchInventoryController(
			BranchInventoryService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping("/branches")
	List<BranchView> branches(HttpServletRequest request) {
		require(request, TenantPermission.VIEW_INVENTORY);
		return service.findBranches();
	}

	@PostMapping("/branches")
	ResponseEntity<BranchView> createBranch(
			@Valid @RequestBody BranchRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
		BranchView created = service.createBranch(body.name(), body.address(), body.defaultBranch());
		return ResponseEntity.created(URI.create("branches/" + created.id())).body(created);
	}

	@PutMapping("/branches/{branchId}")
	BranchView updateBranch(
			@PathVariable UUID branchId,
			@Valid @RequestBody BranchRequest body,
			HttpServletRequest request) {
		require(request, TenantPermission.MANAGE_BASIC_SETTINGS);
		return service.updateBranch(
			branchId, body.name(), body.address(), body.active(), body.defaultBranch());
	}

	@GetMapping("/inventory/variants/{variantId}/branches")
	List<BranchStockView> variantStock(
			@PathVariable UUID variantId,
			HttpServletRequest request) {
		require(request, TenantPermission.VIEW_INVENTORY);
		return service.findVariantStock(variantId);
	}

	@PostMapping("/inventory/variants/{variantId}/branches/{branchId}/adjustments")
	ResponseEntity<BranchAdjustmentView> adjustBranch(
			@PathVariable UUID variantId,
			@PathVariable UUID branchId,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody BranchAdjustmentRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request, TenantPermission.ADJUST_STOCK);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		BranchAdjustmentView result = service.adjust(
			variantId,
			branchId,
			idempotencyKey,
			body.direction(),
			body.quantity(),
			body.reason(),
			body.note(),
			new InventoryActor(principal.publicId(), principal.displayName()));
		return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
	}

	private void require(HttpServletRequest request, TenantPermission permission) {
		permissionGuard.require(request, permission);
	}

	public record BranchRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 255) String address,
		boolean active,
		boolean defaultBranch) {}

	public record BranchAdjustmentRequest(
		@NotNull AdjustmentDirection direction,
		@NotNull BigDecimal quantity,
		@NotNull InventoryReason reason,
		@Size(max = 500) String note) {}
}
