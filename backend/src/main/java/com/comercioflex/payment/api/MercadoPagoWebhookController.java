package com.comercioflex.payment.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.comercioflex.payment.application.MercadoPagoWebhookReceiver;

@RestController
public class MercadoPagoWebhookController {

	private final MercadoPagoWebhookReceiver receiver;

	public MercadoPagoWebhookController(MercadoPagoWebhookReceiver receiver) {
		this.receiver = receiver;
	}

	@PostMapping("/api/v1/integrations/mercado-pago/webhooks")
	ResponseEntity<Void> receive(
			@RequestParam String route,
			@RequestParam(name = "data.id") String dataId,
			@RequestHeader("x-signature") String signature,
			@RequestHeader("x-request-id") String requestId,
			@RequestBody String body) {
		receiver.receive(route, dataId, signature, requestId, body);
		return ResponseEntity.ok().build();
	}
}
