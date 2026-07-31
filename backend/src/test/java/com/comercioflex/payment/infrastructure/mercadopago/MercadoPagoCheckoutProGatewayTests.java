package com.comercioflex.payment.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.preference.Preference;

import com.comercioflex.payment.application.CheckoutPreferenceCommand;
import com.comercioflex.payment.application.CheckoutProProperties;
import com.comercioflex.payment.application.CreatedCheckoutPreference;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.domain.PaymentEnvironment;

class MercadoPagoCheckoutProGatewayTests {

	@Test
	void createsOnlineOnlyPreferenceWithPerRequestCredentialAndNoMarketplaceFee()
			throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		Preference response = mock(Preference.class);
		when(response.getId()).thenReturn("pref-123");
		when(response.getSandboxInitPoint()).thenReturn("https://sandbox.mercadopago.com/pay");
		when(response.getCollectorId()).thenReturn(123456L);
		when(preferences.create(any(PreferenceRequest.class), any(MPRequestOptions.class)))
			.thenReturn(response);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
		CheckoutPreferenceCommand command = new CheckoutPreferenceCommand(
			UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
			"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "Pedido #42",
			new BigDecimal("1500.00"), "ARS",
			URI.create("https://shop.example.test/return/token"),
			URI.create("https://api.example.test/webhooks?route=token"),
			Instant.parse("2026-07-31T17:00:00Z"));

		CreatedCheckoutPreference created = gateway.createPreference(credential, command);

		ArgumentCaptor<PreferenceRequest> request =
			ArgumentCaptor.forClass(PreferenceRequest.class);
		ArgumentCaptor<MPRequestOptions> options =
			ArgumentCaptor.forClass(MPRequestOptions.class);
		org.mockito.Mockito.verify(preferences).create(request.capture(), options.capture());
		assertThat(created.preferenceId()).isEqualTo("pref-123");
		assertThat(options.getValue().getAccessToken()).isEqualTo("TEST-secret-token");
		assertThat(request.getValue().getMarketplaceFee()).isNull();
		assertThat(request.getValue().getBinaryMode()).isFalse();
		assertThat(request.getValue().getPaymentMethods().getExcludedPaymentTypes())
			.extracting(type -> type.getId()).containsExactly("ticket");
		assertThat(request.getValue().getExternalReference())
			.isEqualTo("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	}

	private CheckoutProProperties properties() {
		return new CheckoutProProperties(
			true, "TEST-token", "123456", "demo",
			URI.create("https://api.example.test"), URI.create("https://shop.example.test"),
			"webhook-secret", Duration.ofSeconds(1), Duration.ofSeconds(2),
			Duration.ofSeconds(30), Duration.ofSeconds(30), 3,
			Duration.ofHours(24));
	}
}
