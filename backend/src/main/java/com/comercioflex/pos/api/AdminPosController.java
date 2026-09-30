package com.comercioflex.pos.api;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.pos.application.PosSaleService;
import com.comercioflex.pos.application.PosSaleService.PosCatalogItem;
import com.comercioflex.pos.application.PosSaleService.PosPaymentMethod;
import com.comercioflex.pos.application.PosSaleService.PosSaleException;
import com.comercioflex.pos.application.PosSaleService.PosSaleLine;
import com.comercioflex.pos.application.PosSaleService.PosSaleResult;
import com.comercioflex.pos.application.PosSaleService.PosSaleView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/pos")
public class AdminPosController {

	private final PosSaleService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminPosController(PosSaleService service, TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping("/catalog")
	List<PosCatalogItem> catalog(
			@RequestParam UUID branchId,
			@RequestParam(required = false) String q,
			HttpServletRequest request) {
		require(request);
		return service.searchCatalog(branchId, q);
	}

	@GetMapping("/sales")
	List<PosSaleView> sales(HttpServletRequest request) {
		require(request);
		return service.listSales();
	}

	@PostMapping("/sales")
	ResponseEntity<PosSaleView> createSale(
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody PosSaleRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		PosSaleResult result = service.createSale(
			idempotencyKey,
			body.branchId(),
			body.paymentMethod(),
			body.items().stream().map(item -> new PosSaleLine(item.variantId(), item.quantity())).toList(),
			new InventoryActor(principal.publicId(), principal.displayName()));
		return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
			.body(result.sale());
	}

	@ExceptionHandler(PosSaleException.class)
	ProblemDetail posError(PosSaleException exception) {
		HttpStatus status = switch (exception.failure()) {
			case INVALID -> HttpStatus.BAD_REQUEST;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONFLICT -> HttpStatus.CONFLICT;
		};
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
		detail.setTitle("No se pudo registrar la venta en local");
		detail.setType(URI.create("urn:comercio-flex:pos:" + exception.failure().name().toLowerCase()));
		return detail;
	}

	private void require(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_POS_SALES);
	}

	public record PosSaleRequest(
		@NotNull UUID branchId,
		@NotNull PosPaymentMethod paymentMethod,
		@NotEmpty @Size(max = 100) List<@Valid PosSaleItemRequest> items) {}

	public record PosSaleItemRequest(
		@NotNull UUID variantId,
		@NotNull @Positive BigDecimal quantity) {}
}
