package com.comercioflex.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.TenantContext;

@Service
public class MercadoPagoQrOrderWebhookReceiver {

	private static final Logger LOGGER =
		LoggerFactory.getLogger(MercadoPagoQrOrderWebhookReceiver.class);

	private final QrOrderControlRepository routes;
	private final PaymentCredentialResolver credentials;
	private final QrOrderService orders;
	private final PaymentOAuthProperties oauthProperties;
	private final CheckoutProProperties checkoutProperties;
	private final TenantContext tenantContext;
	private final ObjectMapper objectMapper;

	public MercadoPagoQrOrderWebhookReceiver(
			QrOrderControlRepository routes,
			PaymentCredentialResolver credentials,
			QrOrderService orders,
			PaymentOAuthProperties oauthProperties,
			CheckoutProProperties checkoutProperties,
			TenantContext tenantContext,
			ObjectMapper objectMapper) {
		this.routes = routes;
		this.credentials = credentials;
		this.orders = orders;
		this.oauthProperties = oauthProperties;
		this.checkoutProperties = checkoutProperties;
		this.tenantContext = tenantContext;
		this.objectMapper = objectMapper;
	}

	public void receive(
			String queryOrderId, String signature, String requestId, String rawBody) {
		boolean signaturePresent = signature != null && !signature.isBlank();
		boolean routeResolved = false;
		try {
			requireEnabled();
			validateText(queryOrderId, 100, "INVALID_QR_WEBHOOK");
			validateText(requestId, 100, "INVALID_QR_WEBHOOK");
			validateSignature(signature, queryOrderId, requestId);
			validatePayload(queryOrderId, rawBody);
			QrOrderRoute route = routes.findByProviderOrderId(queryOrderId, environment())
				.orElseThrow(() -> invalid(
					"QR_WEBHOOK_ROUTE_NOT_FOUND", "La orden QR no está registrada."));
			routeResolved = true;
			PaymentCredential credential = credentials.resolve(route.tenantId(), route.tenantSlug());
			try (TenantContext.Scope ignored = tenantContext.open(route.tenantDatabaseKey())) {
				QrOrderProcessingResult result = orders.fetchAndApply(route, credential);
				if (terminal(result)) {
					routes.complete(route.internalId(),
						result == QrOrderProcessingResult.EXPIRED ? "EXPIRED" : "COMPLETED",
						java.time.Instant.now());
				}
			}
			LOGGER.info(
				"event=mp_qr_order_webhook_received signaturePresent={} routeResolved={} environment={} result=PROCESSED",
				signaturePresent, routeResolved, environment());
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
				"event=mp_qr_order_webhook_received signaturePresent={} routeResolved={} environment={} result=REJECTED errorType={}",
				signaturePresent, routeResolved, environment(),
				exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private void validatePayload(String queryOrderId, String rawBody) {
		if (rawBody == null || rawBody.isBlank() || rawBody.length() > 32_768) {
			throw invalid("INVALID_QR_WEBHOOK", "La notificación QR no es válida.");
		}
		try {
			JsonNode root = objectMapper.readTree(rawBody);
			String type = root.path("type").asText("");
			String bodyOrderId = root.path("data").path("id").asText("");
			if (!"order".equalsIgnoreCase(type) || !queryOrderId.equals(bodyOrderId)) {
				throw invalid("INVALID_QR_WEBHOOK", "La notificación QR no es válida.");
			}
		}
		catch (QrOrderException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw invalid("INVALID_QR_WEBHOOK", "La notificación QR no es válida.");
		}
	}

	private void validateSignature(String header, String dataId, String requestId) {
		if (header == null || header.length() > 300) {
			throw invalid("INVALID_QR_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
		String timestamp = null;
		String supplied = null;
		for (String part : header.split(",")) {
			String[] pair = part.trim().split("=", 2);
			if (pair.length == 2 && pair[0].equals("ts")) timestamp = pair[1];
			if (pair.length == 2 && pair[0].equals("v1")) {
				supplied = pair[1].toLowerCase(Locale.ROOT);
			}
		}
		if (timestamp == null || !timestamp.matches("^[0-9]{10,17}$")
				|| supplied == null || !supplied.matches("^[0-9a-f]{64}$")) {
			throw invalid("INVALID_QR_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
		String manifest = "id:" + dataId.toLowerCase(Locale.ROOT)
			+ ";request-id:" + requestId + ";ts:" + timestamp + ";";
		byte[] expected = hmac(manifest);
		byte[] actual = HexFormat.of().parseHex(supplied);
		if (!MessageDigest.isEqual(expected, actual)) {
			throw invalid("INVALID_QR_WEBHOOK_SIGNATURE", "La firma no es válida.");
		}
	}

	private byte[] hmac(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
				checkoutProperties.webhookSecret().getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"));
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("HMAC-SHA256 no está disponible.", exception);
		}
	}

	private void validateText(String value, int maximum, String code) {
		if (value == null || value.isBlank() || value.length() > maximum
				|| value.chars().anyMatch(Character::isISOControl)) {
			throw invalid(code, "La notificación QR no es válida.");
		}
	}

	private boolean terminal(QrOrderProcessingResult result) {
		return result != QrOrderProcessingResult.PENDING;
	}

	private PaymentEnvironment environment() {
		return oauthProperties.environment();
	}

	private void requireEnabled() {
		if (!oauthProperties.enabled() || !checkoutProperties.enabled()
				|| checkoutProperties.webhookSecret() == null
				|| checkoutProperties.webhookSecret().isBlank()) {
			throw invalid("PAYMENTS_NOT_ENABLED", "Los pagos no están habilitados.");
		}
	}

	private QrOrderException invalid(String code, String message) {
		return new QrOrderException(code, message);
	}
}
