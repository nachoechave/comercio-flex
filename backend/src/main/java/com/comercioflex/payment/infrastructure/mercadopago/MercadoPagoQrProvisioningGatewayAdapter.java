package com.comercioflex.payment.infrastructure.mercadopago;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.comercioflex.payment.application.MercadoPagoQrProvisioningGateway;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.QrAuthorizationStatus;
import com.comercioflex.payment.application.QrProviderException;
import com.comercioflex.payment.application.QrProviderPos;
import com.comercioflex.payment.application.QrProviderStore;
import com.comercioflex.payment.application.QrStoreSetupCommand;

public final class MercadoPagoQrProvisioningGatewayAdapter
		implements MercadoPagoQrProvisioningGateway {

	private final RestClient client;

	public MercadoPagoQrProvisioningGatewayAdapter(RestClient client) {
		this.client = client;
	}

	@Override
	public Optional<QrProviderStore> findStore(
			PaymentCredential credential, String externalStoreId) {
		try {
			JsonNode response = client.get()
				.uri(builder -> builder
					.path("/users/{seller}/stores/search")
					.queryParam("external_id", externalStoreId)
					.build(credential.sellerAccountId()))
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(credential.accessToken()))
				.retrieve()
				.body(JsonNode.class);
			return results(response).stream()
				.filter(node -> externalStoreId.equals(text(node, "external_id")))
				.map(this::store)
				.findFirst();
		}
		catch (RestClientResponseException exception) {
			if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
				return Optional.empty();
			}
			throw providerException(exception, false);
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception, true);
		}
	}

	@Override
	public Optional<QrProviderPos> findPos(
			PaymentCredential credential, String externalPosId) {
		try {
			JsonNode response = client.get()
				.uri(builder -> builder
					.path("/v2/pos")
					.queryParam("external_id", externalPosId)
					.build())
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(credential.accessToken()))
				.retrieve()
				.body(JsonNode.class);
			return results(response).stream()
				.filter(node -> externalPosId.equals(text(node, "external_id")))
				.map(this::pos)
				.findFirst();
		}
		catch (RestClientResponseException exception) {
			if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
				return Optional.empty();
			}
			throw providerException(exception, false);
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception, true);
		}
	}

	@Override
	public QrProviderStore createStore(
			PaymentCredential credential,
			String externalStoreId,
			QrStoreSetupCommand command) {
		Map<String, Object> location = new LinkedHashMap<>();
		location.put("street_name", command.streetName());
		location.put("street_number", command.streetNumber());
		location.put("city_name", command.cityName());
		location.put("state_name", command.stateName());
		location.put("latitude", command.latitude());
		location.put("longitude", command.longitude());
		if (command.reference() != null && !command.reference().isBlank()) {
			location.put("reference", command.reference());
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", command.storeName());
		body.put("external_id", externalStoreId);
		body.put("location", location);
		try {
			JsonNode response = client.post()
				.uri("/users/{seller}/stores", credential.sellerAccountId())
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(credential.accessToken()))
				.body(body)
				.retrieve()
				.body(JsonNode.class);
			return store(response);
		}
		catch (RestClientResponseException exception) {
			throw providerException(
				exception, exception.getStatusCode() == HttpStatus.CONFLICT);
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception, true);
		}
	}

	@Override
	public QrProviderPos createPos(
			PaymentCredential credential,
			String providerStoreId,
			String externalStoreId,
			String externalPosId,
			UUID idempotencyKey) {
		Map<String, Object> body = Map.of(
			"name", "Caja QR",
			"store_id", providerStoreId,
			"external_store_id", externalStoreId,
			"external_id", externalPosId,
			"config", Map.of("qr", Map.of("operating_mode", "pdv")));
		try {
			JsonNode response = client.post()
				.uri("/v2/pos")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> {
					headers.setBearerAuth(credential.accessToken());
					headers.set("X-Idempotency-Key", idempotencyKey.toString());
				})
				.body(body)
				.retrieve()
				.body(JsonNode.class);
			return pos(response);
		}
		catch (RestClientResponseException exception) {
			throw providerException(
				exception, exception.getStatusCode() == HttpStatus.CONFLICT);
		}
		catch (ResourceAccessException exception) {
			throw unavailable(exception, true);
		}
	}

	private QrProviderStore store(JsonNode node) {
		String providerId = requiredText(node, "id");
		String externalId = requiredText(node, "external_id");
		return new QrProviderStore(providerId, externalId);
	}

	private QrProviderPos pos(JsonNode node) {
		JsonNode qr = node.path("config").path("qr");
		return new QrProviderPos(
			requiredText(node, "id"),
			requiredText(node, "external_id"),
			text(node, "store_id"),
			text(node, "external_store_id"),
			text(node, "user_id"),
			text(node, "status"),
			text(qr, "operating_mode"));
	}

	private List<JsonNode> results(JsonNode response) {
		List<JsonNode> found = new ArrayList<>();
		collectResults(response, found);
		return found;
	}

	private void collectResults(JsonNode node, List<JsonNode> found) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return;
		}
		if (node.isArray()) {
			node.forEach(item -> collectResults(item, found));
			return;
		}
		JsonNode nested = node.get("results");
		if (nested != null && nested.isArray()) {
			nested.forEach(found::add);
			return;
		}
		if (node.has("id")) {
			found.add(node);
		}
	}

	private String requiredText(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null || value.isBlank()) {
			throw new QrProviderException(
				QrAuthorizationStatus.PROVIDER_ERROR,
				false,
				"Mercado Pago devolvió una respuesta QR incompleta.",
				null);
		}
		return value;
	}

	private String text(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private QrProviderException providerException(
			RestClientResponseException exception, boolean recoveryAllowed) {
		QrAuthorizationStatus category =
			exception.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| exception.getStatusCode() == HttpStatus.FORBIDDEN
				? QrAuthorizationStatus.UNAUTHORIZED_SCOPES
				: QrAuthorizationStatus.PROVIDER_ERROR;
		return new QrProviderException(
			category,
			recoveryAllowed,
			"Mercado Pago no pudo completar la configuración QR.",
			exception);
	}

	private QrProviderException unavailable(
			ResourceAccessException exception, boolean recoveryAllowed) {
		return new QrProviderException(
			QrAuthorizationStatus.PROVIDER_ERROR,
			recoveryAllowed,
			"Mercado Pago no está disponible temporalmente.",
			exception);
	}
}
