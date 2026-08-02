package com.comercioflex.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.comercioflex.payment.domain.PaymentEnvironment;

@Service
public class MercadoPagoWebhookReceiver {

	private static final Duration SIGNATURE_TOLERANCE = Duration.ofMinutes(5);

	private final CheckoutControlRepository repository;
	private final CheckoutProProperties properties;
	private final PaymentOAuthProperties oauthProperties;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactions;
	private final PaymentWebhookMetrics metrics;
	private final Clock clock;

	@Autowired
	public MercadoPagoWebhookReceiver(
			CheckoutControlRepository repository,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			ObjectMapper objectMapper,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactions,
			PaymentWebhookMetrics metrics) {
		this(repository, properties, oauthProperties, objectMapper,
			transactions, metrics, Clock.systemUTC());
	}

	MercadoPagoWebhookReceiver(
			CheckoutControlRepository repository,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			ObjectMapper objectMapper,
			TransactionTemplate transactions,
			PaymentWebhookMetrics metrics,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.oauthProperties = oauthProperties;
		this.objectMapper = objectMapper;
		this.transactions = transactions;
		this.metrics = metrics;
		this.clock = clock;
	}

	public boolean receive(
			String routeToken,
			String signedDataId,
			String signature,
			String requestId,
			String rawBody) {
		requireEnabled();
		validateText(routeToken, 43, "INVALID_WEBHOOK_ROUTE");
		validateText(signedDataId, 100, "INVALID_WEBHOOK");
		validateText(requestId, 100, "INVALID_WEBHOOK");
		validateSignature(signature, signedDataId, requestId);
		JsonNode payload = parse(rawBody);
		String notificationId = text(payload, "id", 100);
		String type = text(payload, "type", 40);
		String action = text(payload, "action", 80);
		String userId = text(payload, "user_id", 100);
		String resourceId = text(payload.path("data"), "id", 100);
		if (!"payment".equals(type) || !resourceId.equals(signedDataId)
				|| !("payment.created".equals(action) || "payment.updated".equals(action))) {
			throw invalid("INVALID_WEBHOOK", "La notificación no es válida.");
		}
		boolean liveMode = payload.path("live_mode").asBoolean(!expectedLiveMode());
		if (liveMode != expectedLiveMode()) {
			throw invalid("WEBHOOK_ENVIRONMENT_MISMATCH", "La notificación no pertenece al ambiente.");
		}
		Instant now = clock.instant();
		boolean inserted = Objects.requireNonNull(transactions.execute(status -> {
			CheckoutRoute route = repository.findRoute(sha256(routeToken), environment())
				.orElseThrow(() -> invalid("INVALID_WEBHOOK_ROUTE", "La ruta no es válida."));
			if (!route.expectedSellerAccountId().equals(userId)) {
				throw invalid("WEBHOOK_SELLER_MISMATCH", "La notificación no pertenece al vendedor.");
			}
			return repository.insertWebhook(route, new ReceivedWebhook(
				notificationId, requestId, type, action, resourceId, userId,
				liveMode, sha256(rawBody)), now);
		}));
		metrics.received(inserted);
		return inserted;
	}

	private void validateSignature(String header, String dataId, String requestId) {
		if (header == null || header.length() > 300) {
			throw invalid("INVALID_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
		String timestamp = null;
		String supplied = null;
		for (String part : header.split(",")) {
			String[] pair = part.trim().split("=", 2);
			if (pair.length == 2 && pair[0].equals("ts")) {
				timestamp = pair[1];
			}
			else if (pair.length == 2 && pair[0].equals("v1")) {
				supplied = pair[1].toLowerCase(java.util.Locale.ROOT);
			}
		}
		if (timestamp == null || supplied == null || !supplied.matches("[0-9a-f]{64}")) {
			throw invalid("INVALID_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
		Instant signedAt;
		try {
			long raw = Long.parseLong(timestamp);
			signedAt = timestamp.length() > 10
				? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
		}
		catch (RuntimeException exception) {
			throw invalid("INVALID_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
		if (Duration.between(signedAt, clock.instant()).abs().compareTo(SIGNATURE_TOLERANCE) > 0) {
			throw invalid("EXPIRED_WEBHOOK_SIGNATURE", "La firma está vencida.");
		}
		String manifest = "id:" + dataId.toLowerCase(java.util.Locale.ROOT)
			+ ";request-id:" + requestId + ";ts:" + timestamp + ";";
		byte[] expected = hmac(manifest);
		byte[] actual = HexFormat.of().parseHex(supplied);
		if (!MessageDigest.isEqual(expected, actual)) {
			throw invalid("INVALID_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
	}

	private JsonNode parse(String rawBody) {
		if (rawBody == null || rawBody.isBlank() || rawBody.length() > 32_768) {
			throw invalid("INVALID_WEBHOOK", "La notificación no es válida.");
		}
		try {
			return objectMapper.readTree(rawBody);
		}
		catch (Exception exception) {
			throw invalid("INVALID_WEBHOOK", "La notificación no es válida.");
		}
	}

	private String text(JsonNode node, String field, int maximum) {
		String value = node.path(field).asText("").trim();
		if (value.isEmpty() || value.length() > maximum) {
			throw invalid("INVALID_WEBHOOK", "La notificación no es válida.");
		}
		return value;
	}

	private void validateText(String value, int exactOrMaximum, String code) {
		boolean token = exactOrMaximum == 43;
		if (value == null || value.isBlank()
				|| (token ? !value.matches("^[A-Za-z0-9_-]{43}$")
					: value.length() > exactOrMaximum)) {
			throw invalid(code, "La notificación no es válida.");
		}
	}

	private byte[] hmac(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
				properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("HMAC-SHA256 no está disponible.", exception);
		}
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	private PaymentEnvironment environment() {
		return oauthProperties.environment();
	}

	private boolean expectedLiveMode() {
		return environment() == PaymentEnvironment.PRODUCTION;
	}

	private void requireEnabled() {
		if (!properties.enabled()) {
			throw invalid("PAYMENTS_NOT_ENABLED", "Los pagos no están habilitados.");
		}
	}

	private CheckoutPaymentException invalid(String code, String message) {
		return new CheckoutPaymentException(code, message);
	}
}
