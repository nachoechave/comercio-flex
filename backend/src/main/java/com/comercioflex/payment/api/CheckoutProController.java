package com.comercioflex.payment.api;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.payment.application.CheckoutInitiation;
import com.comercioflex.payment.application.CheckoutProService;

import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}")
public class CheckoutProController {

	private static final String TOKEN = "^[A-Za-z0-9_-]{43}$";

	private final CheckoutProService service;

	public CheckoutProController(CheckoutProService service) {
		this.service = service;
	}

	@PostMapping("/orders/{orderId}/payments/checkout-pro")
	ResponseEntity<CheckoutInitiationResponse> initiate(
			@PathVariable String storeSlug,
			@PathVariable UUID orderId,
			@RequestParam @Pattern(regexp = TOKEN) String token,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey) {
		CheckoutInitiation initiation = service.initiate(
			storeSlug, orderId, token, idempotencyKey);
		HttpStatus status = initiation.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status)
			.cacheControl(CacheControl.noStore())
			.body(CheckoutInitiationResponse.from(initiation));
	}

	@GetMapping("/payment-returns/{returnToken}")
	ResponseEntity<PaymentReturnResponse> findReturn(
			@PathVariable @Pattern(regexp = TOKEN) String returnToken) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(PaymentReturnResponse.from(service.findReturn(returnToken)));
	}

	@PostMapping("/payment-returns/{returnToken}/reconcile")
	ResponseEntity<PaymentReturnResponse> reconcileReturn(
			@PathVariable String storeSlug,
			@PathVariable @Pattern(regexp = TOKEN) String returnToken,
			@RequestParam @Pattern(regexp = "^[0-9]{1,20}$") String paymentId) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(PaymentReturnResponse.from(
				service.reconcileReturn(storeSlug, returnToken, paymentId)));
	}

	@PostMapping("/payment-returns/{returnToken}/inspect")
	ResponseEntity<PaymentReturnResponse> inspectReturn(
			@PathVariable String storeSlug,
			@PathVariable @Pattern(regexp = TOKEN) String returnToken) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(PaymentReturnResponse.from(
				service.inspectReturn(storeSlug, returnToken)));
	}
}
