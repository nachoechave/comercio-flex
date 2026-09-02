package com.comercioflex.payment.infrastructure.mercadopago;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.comercioflex.payment.application.CreateQrOrderCommand;
import com.comercioflex.payment.application.MercadoPagoQrOrderGateway;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.ProviderQrOrder;
import com.comercioflex.payment.application.QrOrderException;

public final class MercadoPagoQrOrderGatewayAdapter implements MercadoPagoQrOrderGateway {

	private final RestClient client;

	public MercadoPagoQrOrderGatewayAdapter(RestClient client) {
		this.client = client;
	}

	@Override
	public ProviderQrOrder createOrder(
			PaymentCredential credential, CreateQrOrderCommand command) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "qr");
		body.put("total_amount", command.amount().toPlainString());
		body.put("external_reference", command.externalReference());
		body.put("expiration_time", command.expiration().toString());
		body.put("config", Map.of("qr", Map.of(
			"external_pos_id", command.externalPosId(),
			"mode", "dynamic")));
		body.put("transactions", Map.of("payments", java.util.List.of(Map.of(
			"amount", command.amount().toPlainString()))));
		try {
			JsonNode response = client.post()
				.uri("/v1/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> {
					headers.setBearerAuth(credential.accessToken());
					headers.set("X-Idempotency-Key", command.providerIdempotencyKey().toString());
				})
				.body(body)
				.retrieve()
				.body(JsonNode.class);
			return parse(response, true);
		}
		catch (RestClientResponseException exception) {
			throw providerError(exception);
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public ProviderQrOrder getOrder(
			PaymentCredential credential, String providerOrderId) {
		try {
			JsonNode response = client.get()
				.uri("/v1/orders/{id}", providerOrderId)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(credential.accessToken()))
				.retrieve()
				.body(JsonNode.class);
			return parse(response, false);
		}
		catch (RestClientResponseException exception) {
			throw providerError(exception);
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception);
		}
	}

	private ProviderQrOrder parse(JsonNode node, boolean requireQrData) {
		if (node == null || node.isNull()) throw invalidResponse();
		JsonNode qr = node.path("config").path("qr");
		JsonNode payment = firstPayment(node.path("transactions").path("payments"));
		String qrData = text(node.path("type_response"), "qr_data");
		if (requireQrData && blank(qrData)) throw invalidResponse();
		return new ProviderQrOrder(
			required(node, "id"),
			required(node, "type"),
			required(node, "status"),
			text(node, "status_detail"),
			required(node, "external_reference"),
			decimal(node, "total_amount", true),
			firstText(node, "currency", "currency_id"),
			firstText(node, "user_id", "collector_id"),
			booleanValue(node, "live_mode"),
			text(qr, "external_pos_id"),
			qrData,
			text(payment, "id"),
			text(payment, "status"),
			decimal(payment, "amount", false),
			instant(node));
	}

	private JsonNode firstPayment(JsonNode payments) {
		return payments.isArray() && !payments.isEmpty()
			? payments.get(0) : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
	}

	private String required(JsonNode node, String field) {
		String value = text(node, field);
		if (blank(value)) throw invalidResponse();
		return value;
	}

	private String text(JsonNode node, String field) {
		if (node == null || node.isMissingNode() || node.isNull()) return null;
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private String firstText(JsonNode node, String first, String second) {
		String value = text(node, first);
		return blank(value) ? text(node, second) : value;
	}

	private BigDecimal decimal(JsonNode node, String field, boolean required) {
		String value = text(node, field);
		if (blank(value)) {
			if (required) throw invalidResponse();
			return null;
		}
		try {
			return new BigDecimal(value);
		}
		catch (NumberFormatException exception) {
			throw invalidResponse();
		}
	}

	private Boolean booleanValue(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asBoolean();
	}

	private Instant instant(JsonNode node) {
		for (String field : new String[] {"last_updated_date", "date_last_updated", "date_created"}) {
			String value = text(node, field);
			if (!blank(value)) {
				try {
					return OffsetDateTime.parse(value).toInstant();
				}
				catch (java.time.format.DateTimeParseException ignored) {
					// Provider timestamps are optional metadata; lifecycle validation is independent.
				}
			}
		}
		return null;
	}

	private QrOrderException providerError(RestClientResponseException exception) {
		return new QrOrderException(
			"QR_PROVIDER_HTTP_" + exception.getStatusCode().value(),
			"Mercado Pago no pudo consultar la orden QR.",
			exception.getStatusCode().is5xxServerError(), exception);
	}

	private QrOrderException unavailable(ResourceAccessException exception) {
		return new QrOrderException(
			"QR_PROVIDER_UNAVAILABLE", "Mercado Pago no está disponible temporalmente.",
			true, exception);
	}

	private QrOrderException invalidResponse() {
		return new QrOrderException(
			"QR_PROVIDER_INVALID_RESPONSE", "Mercado Pago devolvió una orden QR inválida.");
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
