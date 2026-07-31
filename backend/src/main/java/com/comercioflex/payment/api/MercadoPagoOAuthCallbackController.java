package com.comercioflex.payment.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.payment.application.MerchantPaymentConnectionService;
import com.comercioflex.payment.application.OAuthCallbackResult;
import com.comercioflex.payment.application.PaymentOAuthCallbackException;
import com.comercioflex.payment.application.PaymentOAuthProperties;

@RestController
@RequestMapping("/api/v1/integrations/mercado-pago/oauth")
public class MercadoPagoOAuthCallbackController {

	private final MerchantPaymentConnectionService service;
	private final PaymentOAuthProperties properties;

	public MercadoPagoOAuthCallbackController(
			MerchantPaymentConnectionService service,
			PaymentOAuthProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@GetMapping("/callback")
	ResponseEntity<Void> callback(
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String error,
			Authentication authentication) {
		try {
			OAuthCallbackResult result = service.complete(
				state, code, error, (PlatformPrincipal) authentication.getPrincipal());
			return redirect(result.tenantSlug(), result.outcome());
		}
		catch (PaymentOAuthCallbackException exception) {
			return redirect(exception.tenantSlug(), "failed");
		}
	}

	private ResponseEntity<Void> redirect(String tenantSlug, String outcome) {
		URI location = UriComponentsBuilder.fromUri(properties.frontendBaseUri())
			.pathSegment("tiendas", tenantSlug, "admin", "configuracion", "pagos")
			.queryParam("oauth", outcome)
			.build()
			.encode()
			.toUri();
		return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
	}
}
