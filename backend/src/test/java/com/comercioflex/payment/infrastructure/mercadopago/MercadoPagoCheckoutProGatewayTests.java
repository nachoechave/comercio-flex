package com.comercioflex.payment.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.net.MPElementsResourcesPage;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.merchantorder.MerchantOrderCollector;
import com.mercadopago.resources.merchantorder.MerchantOrderPayment;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentOrder;

import com.comercioflex.payment.application.CheckoutPreferenceCommand;
import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.CheckoutProProperties;
import com.comercioflex.payment.application.CreatedCheckoutPreference;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.ProviderCheckoutState;
import com.comercioflex.payment.application.VerifiedProviderPayment;
import com.comercioflex.payment.domain.PaymentEnvironment;

class MercadoPagoCheckoutProGatewayTests {

	@Test
	void reportsNoPaymentOnlyForAValidatedEmptyMerchantOrder() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		@SuppressWarnings("unchecked")
		MPElementsResourcesPage<MerchantOrder> page = mock(MPElementsResourcesPage.class);
		MerchantOrder order = mock(MerchantOrder.class);
		MerchantOrderCollector collector = mock(MerchantOrderCollector.class);
		when(page.getElements()).thenReturn(List.of(order));
		when(order.getPreferenceId()).thenReturn("pref-123");
		when(order.getExternalReference()).thenReturn("external-123");
		when(order.getCollector()).thenReturn(collector);
		when(collector.getId()).thenReturn(123456L);
		when(order.getPayments()).thenReturn(List.of());
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		ProviderCheckoutState result = gateway.inspectPreference(
			credential, "pref-123", "external-123");

		assertThat(result).isEqualTo(ProviderCheckoutState.NO_PAYMENT_RECORDED);
	}

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
		assertThat(request.getValue().getBackUrls().getSuccess())
			.isEqualTo("https://shop.example.test/return/token");
		assertThat(request.getValue().getBackUrls().getFailure())
			.isEqualTo("https://shop.example.test/return/token");
		assertThat(request.getValue().getBackUrls().getPending())
			.isEqualTo("https://shop.example.test/return/token");
		assertThat(request.getValue().getAutoReturn()).isEqualTo("approved");
		assertThat(request.getValue().getNotificationUrl())
			.isEqualTo("https://api.example.test/webhooks?route=token");
	}

	@Test
	void discoversAndVerifiesAnApprovedPaymentFromItsPreference() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		@SuppressWarnings("unchecked")
		MPElementsResourcesPage<MerchantOrder> page = mock(MPElementsResourcesPage.class);
		MerchantOrder order = mock(MerchantOrder.class);
		MerchantOrderCollector collector = mock(MerchantOrderCollector.class);
		MerchantOrderPayment summary = mock(MerchantOrderPayment.class);
		Payment payment = mock(Payment.class);
		PaymentOrder paymentOrder = mock(PaymentOrder.class);
		when(page.getElements()).thenReturn(List.of(order));
		when(order.getPreferenceId()).thenReturn("pref-123");
		when(order.getExternalReference()).thenReturn("external-123");
		when(order.getCollector()).thenReturn(collector);
		when(collector.getId()).thenReturn(123456L);
		when(order.getPayments()).thenReturn(List.of(summary));
		when(summary.getId()).thenReturn(998877L);
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		when(merchantOrders.get(org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(order);
		when(payment.getId()).thenReturn(998877L);
		when(payment.getCollectorId()).thenReturn(123456L);
		when(payment.getOrder()).thenReturn(paymentOrder);
		when(paymentOrder.getId()).thenReturn(77L);
		when(payment.getExternalReference()).thenReturn("external-123");
		when(payment.getTransactionAmount()).thenReturn(new BigDecimal("5.00"));
		when(payment.getCurrencyId()).thenReturn("ARS");
		when(payment.isLiveMode()).thenReturn(true);
		when(payment.getStatus()).thenReturn("approved");
		when(payments.get(org.mockito.ArgumentMatchers.eq(998877L), any(MPRequestOptions.class)))
			.thenReturn(payment);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		VerifiedProviderPayment result = gateway.findPaymentForPreference(
			credential, "pref-123", "external-123").orElseThrow();

		assertThat(result.providerPaymentId()).isEqualTo("998877");
		assertThat(result.preferenceId()).isEqualTo("pref-123");
		assertThat(result.status().name()).isEqualTo("APPROVED");
	}

	@Test
	void returnsEmptyWhenThePreferenceHasNoMerchantOrder() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		@SuppressWarnings("unchecked")
		MPElementsResourcesPage<MerchantOrder> page = mock(MPElementsResourcesPage.class);
		when(page.getElements()).thenReturn(List.of());
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		assertThat(gateway.findPaymentForPreference(
			credential, "pref-123", "external-123")).isEmpty();
	}

	@Test
	void exposesSafeProviderSearchDiagnosticsWithoutTheProviderPayload() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		MPApiException providerFailure = new MPApiException(
			"provider failed",
			new MPResponse(503, Map.of(),
				"{\"error\":\"bad_request\",\"message\":\"sensitive-provider-payload\"}"));
		when(merchantOrders.search(any(), any(MPRequestOptions.class)))
			.thenThrow(providerFailure);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		assertThatThrownBy(() -> gateway.findPaymentForPreference(
			credential, "pref-123", "external-123"))
			.isInstanceOf(CheckoutPaymentException.class)
			.satisfies(exception -> {
				CheckoutPaymentException failure = (CheckoutPaymentException) exception;
				assertThat(failure.code()).isEqualTo("PREFERENCE_LOOKUP_FAILED");
				assertThat(failure.reconciliationDiagnostics().stage())
					.isEqualTo("PROVIDER_SEARCH");
				assertThat(failure.reconciliationDiagnostics().reason())
					.isEqualTo("PREFERENCE_LOOKUP_FAILED");
				assertThat(failure.reconciliationDiagnostics().providerHttpStatus())
					.isEqualTo(503);
				assertThat(failure.reconciliationDiagnostics().providerErrorCode())
					.isEqualTo("bad_request");
				assertThat(failure.reconciliationDiagnostics().resultCount()).isNull();
				assertThat(failure.getMessage()).doesNotContain("sensitive-provider-payload");
			});
	}

	@Test
	void reportsSellerMismatchWithTheMerchantOrderResultCount() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		@SuppressWarnings("unchecked")
		MPElementsResourcesPage<MerchantOrder> page = mock(MPElementsResourcesPage.class);
		MerchantOrder order = mock(MerchantOrder.class);
		MerchantOrderCollector collector = mock(MerchantOrderCollector.class);
		when(page.getElements()).thenReturn(List.of(order));
		when(order.getPreferenceId()).thenReturn("pref-123");
		when(order.getExternalReference()).thenReturn("external-123");
		when(order.getCollector()).thenReturn(collector);
		when(collector.getId()).thenReturn(999999L);
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		assertThatThrownBy(() -> gateway.findPaymentForPreference(
			credential, "pref-123", "external-123"))
			.isInstanceOf(CheckoutPaymentException.class)
			.satisfies(exception -> {
				CheckoutPaymentException failure = (CheckoutPaymentException) exception;
				assertThat(failure.code()).isEqualTo("INVALID_PROVIDER_RESPONSE");
				assertThat(failure.reconciliationDiagnostics().stage())
					.isEqualTo("SELLER_VALIDATION");
				assertThat(failure.reconciliationDiagnostics().reason())
					.isEqualTo("SELLER_MISMATCH");
				assertThat(failure.reconciliationDiagnostics().resultCount()).isEqualTo(1);
			});
	}

	@Test
	void selectsTheApprovedPaymentWhenTheMerchantOrderContainsMultiplePayments()
			throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		@SuppressWarnings("unchecked")
		MPElementsResourcesPage<MerchantOrder> page = mock(MPElementsResourcesPage.class);
		MerchantOrder order = mock(MerchantOrder.class);
		MerchantOrderCollector collector = mock(MerchantOrderCollector.class);
		MerchantOrderPayment pendingSummary = mock(MerchantOrderPayment.class);
		MerchantOrderPayment approvedSummary = mock(MerchantOrderPayment.class);
		when(page.getElements()).thenReturn(List.of(order));
		when(order.getPreferenceId()).thenReturn("pref-123");
		when(order.getExternalReference()).thenReturn("external-123");
		when(order.getCollector()).thenReturn(collector);
		when(collector.getId()).thenReturn(123456L);
		when(order.getPayments()).thenReturn(List.of(pendingSummary, approvedSummary));
		when(pendingSummary.getId()).thenReturn(1001L);
		when(approvedSummary.getId()).thenReturn(1002L);
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		when(merchantOrders.get(any(), any(MPRequestOptions.class))).thenReturn(order);
		Payment pendingPayment = providerPayment(1001L, "pending");
		Payment approvedPayment = providerPayment(1002L, "approved");
		when(payments.get(
			org.mockito.ArgumentMatchers.eq(1001L), any(MPRequestOptions.class)))
			.thenReturn(pendingPayment);
		when(payments.get(
			org.mockito.ArgumentMatchers.eq(1002L), any(MPRequestOptions.class)))
			.thenReturn(approvedPayment);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		VerifiedProviderPayment result = gateway.findPaymentForPreference(
			credential, "pref-123", "external-123").orElseThrow();

		assertThat(result.providerPaymentId()).isEqualTo("1002");
		assertThat(result.status().name()).isEqualTo("APPROVED");
	}

	private Payment providerPayment(long id, String status) {
		Payment payment = mock(Payment.class);
		PaymentOrder paymentOrder = mock(PaymentOrder.class);
		when(payment.getId()).thenReturn(id);
		when(payment.getCollectorId()).thenReturn(123456L);
		when(payment.getOrder()).thenReturn(paymentOrder);
		when(paymentOrder.getId()).thenReturn(77L);
		when(payment.getExternalReference()).thenReturn("external-123");
		when(payment.getTransactionAmount()).thenReturn(new BigDecimal("5.00"));
		when(payment.getCurrencyId()).thenReturn("ARS");
		when(payment.isLiveMode()).thenReturn(true);
		when(payment.getStatus()).thenReturn(status);
		return payment;
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
