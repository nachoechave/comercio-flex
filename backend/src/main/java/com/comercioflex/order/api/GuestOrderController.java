package com.comercioflex.order.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.order.application.CreateGuestOrderCommand;
import com.comercioflex.order.application.GuestOrderCreation;
import com.comercioflex.order.application.GuestOrderService;
import com.comercioflex.order.application.OrderItemCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/orders")
public class GuestOrderController {

	private static final String TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$";

	private final GuestOrderService service;

	public GuestOrderController(GuestOrderService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<CreatedGuestOrderResponse> create(
			@PathVariable String storeSlug,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody CreateGuestOrderRequest body) {
		GuestOrderCreation creation = service.create(new CreateGuestOrderCommand(
			idempotencyKey,
			body.customerName(),
			body.customerPhone(),
			body.customerEmail(),
			body.notes(),
			body.paymentMethod(),
			body.items().stream()
				.map(item -> new OrderItemCommand(
					item.variantId(),
					item.decimalQuantity()))
				.toList()));
		HttpStatus status = creation.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
		URI location = URI.create(
			"/api/v1/stores/%s/orders/%s".formatted(
				storeSlug,
				creation.order().id()));
		return ResponseEntity.status(status)
			.location(location)
			.cacheControl(CacheControl.noStore())
			.body(CreatedGuestOrderResponse.from(creation));
	}

	@GetMapping("/{orderId}")
	ResponseEntity<GuestOrderResponse> find(
			@PathVariable UUID orderId,
			@RequestParam @Pattern(regexp = TOKEN_PATTERN) String token) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(GuestOrderResponse.from(service.find(orderId, token)));
	}
}
