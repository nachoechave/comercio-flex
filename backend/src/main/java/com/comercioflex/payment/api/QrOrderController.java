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

import com.comercioflex.payment.application.QrOrderInitiation;
import com.comercioflex.payment.application.QrOrderService;

import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/orders/{orderId}/payments/qr")
public class QrOrderController {

	private static final String TOKEN = "^[A-Za-z0-9_-]{43}$";
	private final QrOrderService service;

	public QrOrderController(QrOrderService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<QrOrderResponse> initiate(
			@PathVariable String storeSlug,
			@PathVariable UUID orderId,
			@RequestParam @Pattern(regexp = TOKEN) String token,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey) {
		QrOrderInitiation result = service.initiate(
			storeSlug, orderId, token, idempotencyKey);
		return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(QrOrderResponse.from(result));
	}

	@GetMapping
	ResponseEntity<QrOrderResponse> current(
			@PathVariable String storeSlug,
			@PathVariable UUID orderId,
			@RequestParam @Pattern(regexp = TOKEN) String token) {
		return service.findCurrent(storeSlug, orderId, token)
			.map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(QrOrderResponse.from(value)))
			.orElseGet(() -> ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore()).build());
	}
}
