package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.comercioflex.payment.domain.PaymentEnvironment;

class MercadoPagoWebhookReceiverTests {

	private static final Instant NOW = Instant.parse("2026-07-31T16:00:00Z");
	private static final String SECRET = "webhook-test-secret-with-enough-entropy";
	private static final String ROUTE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private final CheckoutControlRepository repository = mock(CheckoutControlRepository.class);
	private MercadoPagoWebhookReceiver receiver;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		receiver = new MercadoPagoWebhookReceiver(
			repository, checkoutProperties(), oauthProperties(), new ObjectMapper(),
			transactions, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void verifiesSignatureRouteSellerAndPersistsNormalizedEvent() throws Exception {
		String dataId = "998877";
		String requestId = "request-1";
		String timestamp = Long.toString(NOW.getEpochSecond());
		String signature = "ts=" + timestamp + ",v1=" + signature(
			"id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";");
		CheckoutRoute route = route();
		when(repository.findRoute(any(), any())).thenReturn(Optional.of(route));
		when(repository.insertWebhook(any(), any(), any())).thenReturn(true);
		String body = """
			{"id":"notification-1","type":"payment","action":"payment.updated",
			 "user_id":"123456","live_mode":false,"data":{"id":"998877"}}
			""";

		boolean inserted = receiver.receive(
			ROUTE, dataId, signature, requestId, body);

		assertThat(inserted).isTrue();
		verify(repository).insertWebhook(any(), any(ReceivedWebhook.class), any());
	}

	@Test
	void rejectsInvalidSignatureBeforeLookingUpRoute() {
		assertThatThrownBy(() -> receiver.receive(
			ROUTE, "998877", "ts=1785513600,v1=" + "0".repeat(64),
			"request-1", "{}"))
			.isInstanceOf(CheckoutPaymentException.class)
			.extracting(exception -> ((CheckoutPaymentException) exception).code())
			.isEqualTo("INVALID_WEBHOOK_SIGNATURE");

		verify(repository, never()).findRoute(any(), any());
	}

	private String signature(String manifest) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
	}

	private CheckoutRoute route() throws Exception {
		return new CheckoutRoute(
			1L, UUID.randomUUID(), 9L, "demo", "tenant-demo", PaymentEnvironment.TEST,
			UUID.randomUUID(), "123456", "pref-1", "ACTIVE", NOW.plusSeconds(1800));
	}

	private CheckoutProProperties checkoutProperties() {
		return new CheckoutProProperties(
			true, "TEST-token", "123456", "demo",
			URI.create("https://api.example.test"), URI.create("https://shop.example.test"),
			SECRET, Duration.ofSeconds(1), Duration.ofSeconds(2),
			Duration.ofSeconds(30), Duration.ofSeconds(30), 3,
			Duration.ofHours(24));
	}

	private PaymentOAuthProperties oauthProperties() {
		return new PaymentOAuthProperties(
			true, PaymentEnvironment.TEST, "client", "secret",
			URI.create("https://api.example.test/oauth"), null, null, null,
			URI.create("https://shop.example.test"), Duration.ofSeconds(1),
			Duration.ofSeconds(2), "v1", "key");
	}
}
