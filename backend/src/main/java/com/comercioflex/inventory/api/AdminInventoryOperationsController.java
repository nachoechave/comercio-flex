package com.comercioflex.inventory.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.inventory.application.InventoryOperationsService;
import com.comercioflex.inventory.application.InventoryOperationsService.LowStockSummary;
import com.comercioflex.inventory.application.InventoryOperationsService.MovementView;
import com.comercioflex.inventory.application.InventoryOperationsService.TransferLine;
import com.comercioflex.inventory.application.InventoryOperationsService.TransferResult;
import com.comercioflex.inventory.application.InventoryOperationsService.TransferView;
import com.comercioflex.inventory.domain.InventoryActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/inventory/operations")
public class AdminInventoryOperationsController {

	private final InventoryOperationsService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminInventoryOperationsController(
			InventoryOperationsService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping("/movements")
	List<MovementView> movements(
			@RequestParam(required = false) UUID branchId,
			@RequestParam(defaultValue = "100") int limit,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_INVENTORY);
		return service.listMovements(branchId, limit);
	}

	@GetMapping("/alerts")
	LowStockSummary alerts(
			@RequestParam(required = false) UUID branchId,
			@RequestParam(defaultValue = "100") int limit,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_INVENTORY);
		return service.lowStockAlerts(branchId, limit);
	}

	@GetMapping("/transfers")
	List<TransferView> transfers(
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.VIEW_INVENTORY);
		return service.listTransfers(limit);
	}

	@PostMapping("/transfers")
	ResponseEntity<TransferView> createTransfer(
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody TransferRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		permissionGuard.require(request, TenantPermission.ADJUST_STOCK);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		TransferResult result = service.transfer(
			idempotencyKey,
			body.fromBranchId(),
			body.toBranchId(),
			body.items().stream()
				.map(item -> new TransferLine(item.variantId(), item.quantity()))
				.toList(),
			body.note(),
			new InventoryActor(principal.publicId(), principal.displayName()));
		return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
			.body(result.transfer());
	}

	public record TransferRequest(
		@NotNull UUID fromBranchId,
		@NotNull UUID toBranchId,
		@NotEmpty @Size(max = 100) List<@Valid TransferItemRequest> items,
		@Size(max = 500) String note) {}

	public record TransferItemRequest(
		@NotNull UUID variantId,
		@NotNull @Positive BigDecimal quantity) {}
}
