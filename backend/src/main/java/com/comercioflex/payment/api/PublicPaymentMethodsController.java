package com.comercioflex.payment.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.payment.application.PaymentMethodsService;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/payment-methods")
public class PublicPaymentMethodsController {

	private final PaymentMethodsService service;

	public PublicPaymentMethodsController(PaymentMethodsService service) {
		this.service = service;
	}

	@GetMapping
	ResponseEntity<PaymentMethodsResponse> find(@PathVariable String storeSlug) {
		return ResponseEntity.ok().cacheControl(CacheControl.noCache())
			.body(PaymentMethodsResponse.from(service.find(storeSlug)));
	}
}
