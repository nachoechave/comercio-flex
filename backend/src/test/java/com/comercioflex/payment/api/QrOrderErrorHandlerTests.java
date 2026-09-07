package com.comercioflex.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.comercioflex.payment.application.QrOrderException;

class QrOrderErrorHandlerTests {

	private final QrOrderErrorHandler handler = new QrOrderErrorHandler();

	@Test
	void mapsProviderServerFailuresToBadGateway() {
		var detail = handler.handle(new QrOrderException(
			"QR_PROVIDER_HTTP_500", "Proveedor no disponible.", true, null));

		assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
	}

	@Test
	void mapsProviderRateLimitingToServiceUnavailable() {
		var detail = handler.handle(new QrOrderException(
			"QR_PROVIDER_HTTP_429", "Proveedor limitado.", true, null));

		assertThat(detail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
	}
}
