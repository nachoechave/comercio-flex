package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.TenantContext;

class MercadoPagoQrOrderWebhookReceiverTests {

	private static final String SECRET = "qr-webhook-secret-with-enough-entropy";
	private static final String ORDER_ID = "provider-order";
	private final QrOrderControlRepository routes = mock(QrOrderControlRepository.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final QrOrderService orders = mock(QrOrderService.class);
	private final TenantContext tenantContext = new TenantContext();
	private MercadoPagoQrOrderWebhookReceiver receiver;

	@BeforeEach
	void setUp() {
		receiver = new MercadoPagoQrOrderWebhookReceiver(
			routes, credentials, orders, oauthProperties(), checkoutProperties(),
			tenantContext, new ObjectMapper());
	}

	@Test
	void authenticNotificationResolvesTenantAndFetchesProviderOrderBeforeCompleting() throws Exception {
		QrOrderRoute route = route();
		PaymentCredential credential = credential();
		when(routes.findByProviderOrderId(ORDER_ID, PaymentEnvironment.PRODUCTION))
			.thenReturn(Optional.of(route));
		when(credentials.resolve(route.tenantId(), route.tenantSlug())).thenReturn(credential);
		when(orders.fetchAndApply(route, credential)).thenAnswer(invocation -> {
			org.assertj.core.api.Assertions.assertThat(tenantContext.currentDatabaseKey())
				.contains("tenant_demo");
			return QrOrderProcessingResult.APPROVED;
		});

		receiver.receive(ORDER_ID, signature(ORDER_ID, "request-1", "1788285600"),
			"request-1", "{\"type\":\"order\",\"data\":{\"id\":\"provider-order\"}}");

		verify(orders).fetchAndApply(route, credential);
		verify(routes).complete(any(Long.class), org.mockito.ArgumentMatchers.eq("COMPLETED"),
			any(Instant.class));
		org.assertj.core.api.Assertions.assertThat(tenantContext.currentDatabaseKey()).isEmpty();
	}

	@Test
	void invalidSignatureIsRejectedBeforeRouteResolution() {
		assertThatThrownBy(() -> receiver.receive(
			ORDER_ID, "ts=1788285600,v1=" + "0".repeat(64), "request-1",
			"{\"type\":\"order\",\"data\":{\"id\":\"provider-order\"}}"))
			.isInstanceOf(QrOrderException.class)
			.extracting(exception -> ((QrOrderException) exception).code())
			.isEqualTo("INVALID_QR_WEBHOOK_SIGNATURE");

		verify(routes, never()).findByProviderOrderId(any(), any());
	}

	@Test
	void missingSignatureIsRejectedInsideTheReceiverBeforeRouteResolution() {
		assertThatThrownBy(() -> receiver.receive(
			ORDER_ID, null, "request-1",
			"{\"type\":\"order\",\"data\":{\"id\":\"provider-order\"}}"))
			.isInstanceOf(QrOrderException.class)
			.extracting(exception -> ((QrOrderException) exception).code())
			.isEqualTo("INVALID_QR_WEBHOOK_SIGNATURE");

		verify(routes, never()).findByProviderOrderId(any(), any());
	}

	@Test
	void bodyOrderMustMatchSignedQueryOrder() throws Exception {
		assertThatThrownBy(() -> receiver.receive(
			ORDER_ID, signature(ORDER_ID, "request-1", "1788285600"), "request-1",
			"{\"type\":\"order\",\"data\":{\"id\":\"different\"}}"))
			.isInstanceOf(QrOrderException.class)
			.extracting(exception -> ((QrOrderException) exception).code())
			.isEqualTo("INVALID_QR_WEBHOOK");

		verify(routes, never()).findByProviderOrderId(any(), any());
	}

	private String signature(String orderId, String requestId, String timestamp)
			throws Exception {
		String manifest = "id:" + orderId + ";request-id:" + requestId
			+ ";ts:" + timestamp + ";";
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(
			mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
	}

	private QrOrderRoute route() {
		return new QrOrderRoute(
			7L, 1L, "tiendademo", "tenant_demo", PaymentEnvironment.PRODUCTION,
			UUID.randomUUID(), ORDER_ID, "seller", "ACTIVE", 0,
			Instant.parse("2026-09-01T20:00:00Z"));
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"access-token", "seller", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private PaymentOAuthProperties oauthProperties() {
		return new PaymentOAuthProperties(
			true, PaymentEnvironment.PRODUCTION, "client", "secret",
			URI.create("https://shop.example.test/oauth/callback"), null, null, null,
			URI.create("https://shop.example.test"), Duration.ofSeconds(1),
			Duration.ofSeconds(2), "v1", "encryption-key");
	}

	private CheckoutProProperties checkoutProperties() {
		return new CheckoutProProperties(
			true, null, null, null, URI.create("https://api.example.test"),
			URI.create("https://shop.example.test"), SECRET, Duration.ofSeconds(1),
			Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofSeconds(30),
			3, Duration.ofHours(24));
	}
}
