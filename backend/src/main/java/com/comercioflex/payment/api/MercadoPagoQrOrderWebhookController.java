package com.comercioflex.payment.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.payment.application.MercadoPagoQrOrderWebhookReceiver;

@RestController
public class MercadoPagoQrOrderWebhookController {

	private final MercadoPagoQrOrderWebhookReceiver receiver;

	public MercadoPagoQrOrderWebhookController(
			MercadoPagoQrOrderWebhookReceiver receiver) {
		this.receiver = receiver;
	}

	@PostMapping("/api/v1/integrations/mercado-pago/orders/webhook")
	ResponseEntity<Void> receive(
			@RequestParam("data.id") String orderId,
			@RequestHeader(value = "x-signature", required = false) String signature,
			@RequestHeader(value = "x-request-id", required = false) String requestId,
			@RequestBody String rawBody) {
		receiver.receive(orderId, signature, requestId, rawBody);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
	}
}
