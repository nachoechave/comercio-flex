package com.comercioflex.payment.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantPermissionGuard;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.payment.application.BankTransferPaymentService;
import com.comercioflex.payment.application.DownloadedPaymentReceipt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/admin/bank-transfer-payments")
public class AdminBankTransferController {

	private final BankTransferPaymentService service;
	private final TenantPermissionGuard permissionGuard;

	public AdminBankTransferController(
			BankTransferPaymentService service,
			TenantPermissionGuard permissionGuard) {
		this.service = service;
		this.permissionGuard = permissionGuard;
	}

	@GetMapping
	List<AdminBankTransferPaymentResponse> findPending(HttpServletRequest request) {
		require(request);
		return service.findPendingReview().stream()
			.map(AdminBankTransferPaymentResponse::from).toList();
	}

	@GetMapping("/{paymentId}")
	AdminBankTransferPaymentResponse find(
			@PathVariable UUID paymentId, HttpServletRequest request) {
		require(request);
		return AdminBankTransferPaymentResponse.from(service.findAdmin(paymentId));
	}

	@GetMapping("/{paymentId}/receipt")
	ResponseEntity<byte[]> receipt(
			@PathVariable UUID paymentId, HttpServletRequest request) {
		require(request);
		DownloadedPaymentReceipt receipt = service.download(paymentId);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
				.filename(receipt.originalFilename(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.contentType(org.springframework.http.MediaType.parseMediaType(
				receipt.object().contentType()))
			.body(receipt.object().bytes());
	}

	@PostMapping("/{paymentId}/approve")
	AdminBankTransferPaymentResponse approve(
			@PathVariable UUID paymentId,
			HttpServletRequest request,
			Authentication authentication) {
		require(request);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		return AdminBankTransferPaymentResponse.from(
			service.approve(paymentId, principal.id()));
	}

	@PostMapping("/{paymentId}/reject")
	AdminBankTransferPaymentResponse reject(
			@PathVariable UUID paymentId,
			@Valid @RequestBody RejectBankTransferRequest body,
			HttpServletRequest request,
			Authentication authentication) {
		require(request);
		PlatformPrincipal principal = (PlatformPrincipal) authentication.getPrincipal();
		return AdminBankTransferPaymentResponse.from(
			service.reject(paymentId, principal.id(), body.reason()));
	}

	private void require(HttpServletRequest request) {
		permissionGuard.require(request, TenantPermission.MANAGE_ORDERS);
	}
}
