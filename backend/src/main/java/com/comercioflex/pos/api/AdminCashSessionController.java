package com.comercioflex.pos.api;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.pos.application.CashSessionService;
import com.comercioflex.pos.application.CashSessionService.CashSessionView;
import com.comercioflex.pos.application.PosSaleService.PosSaleException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/pos/cash-sessions")
public class AdminCashSessionController {

	private final CashSessionService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminCashSessionController(
			CashSessionService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping("/current")
	CashSessionView current(
			@RequestParam UUID branchId,
			HttpServletRequest request) {
		require(request);
		return service.current(branchId);
	}

	@GetMapping
	List<CashSessionView> list(
			@RequestParam(required = false) UUID branchId,
			@RequestParam(defaultValue = "30") int limit,
			HttpServletRequest request) {
		require(request);
		return service.list(branchId, limit);
	}

	@PostMapping("/open")
	CashSessionView open(
			@Valid @RequestBody OpenCashSessionRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		return service.open(
			body.branchId(), body.openingAmount(),
			new InventoryActor(principal.publicId(), principal.displayName()));
	}

	@PostMapping("/{sessionId}/close")
	CashSessionView close(
			@PathVariable UUID sessionId,
			@Valid @RequestBody CloseCashSessionRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		return service.close(
			sessionId, body.closingAmount(),
			new InventoryActor(principal.publicId(), principal.displayName()));
	}

	@ExceptionHandler(PosSaleException.class)
	ProblemDetail cashError(PosSaleException exception) {
		HttpStatus status = switch (exception.failure()) {
			case INVALID -> HttpStatus.BAD_REQUEST;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case CONFLICT -> HttpStatus.CONFLICT;
		};
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
		detail.setTitle("No se pudo operar la caja");
		detail.setType(URI.create("urn:comercio-flex:cash:" + exception.failure().name().toLowerCase()));
		return detail;
	}

	private void require(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_POS_SALES);
	}

	public record OpenCashSessionRequest(
		@NotNull UUID branchId,
		@NotNull @PositiveOrZero BigDecimal openingAmount) {}

	public record CloseCashSessionRequest(
		@NotNull @PositiveOrZero BigDecimal closingAmount) {}
}
