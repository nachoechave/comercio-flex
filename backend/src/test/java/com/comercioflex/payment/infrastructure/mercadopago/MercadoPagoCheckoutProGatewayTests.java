package com.comercioflex.payment.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.reflect.TypeToken;
import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.resources.preference.PreferenceSearch;
import com.mercadopago.net.MPElementsResourcesPage;
import com.mercadopago.net.MPResponse;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.merchantorder.MerchantOrderCollector;
import com.mercadopago.resources.merchantorder.MerchantOrderPayment;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentOrder;
import com.mercadopago.serialization.Serializer;

import com.comercioflex.payment.application.CheckoutPreferenceCommand;
import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.CheckoutProProperties;
import com.comercioflex.payment.application.CreatedCheckoutPreference;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.PreferenceSearchDiagnostics;
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
		MPElementsResourcesPage<MerchantOrder> page = merchantOrderPage("""
			{
			  "elements": [{
			    "id": 77,
			    "preference_id": "pref-123",
			    "external_reference": "external-123",
			    "collector": {"id": 123456},
			    "payments": [{"id": 998877, "status": "approved"}]
			  }],
			  "next_offset": 1,
			  "total": 1
			}
			""");
		MerchantOrder order = page.getElements().getFirst();
		Payment payment = mock(Payment.class);
		PaymentOrder paymentOrder = mock(PaymentOrder.class);
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
			credential, "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS").orElseThrow();

		assertThat(result.providerPaymentId()).isEqualTo("998877");
		assertThat(result.preferenceId()).isEqualTo("pref-123");
		assertThat(result.status().name()).isEqualTo("APPROVED");
		verify(payments, never()).search(any(), any(MPRequestOptions.class));
	}

	@Test
	void returnsEmptyWhenThePreferenceHasNoMerchantOrder() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		MPElementsResourcesPage<MerchantOrder> page = merchantOrderPage("""
			{"elements": [], "next_offset": 0, "total": 0}
			""");
		MPResultsResourcesPage<Payment> fallbackPage = paymentSearchPage();
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		when(payments.search(any(), any(MPRequestOptions.class)))
			.thenReturn(fallbackPage);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());
		PaymentCredential credential = new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		assertThat(gateway.findPaymentForPreference(
			credential, "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS")).isEmpty();
	}

	@Test
	void acceptsAValidEmptySdkResponseWithoutPaginationFields() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		MPElementsResourcesPage<MerchantOrder> page = merchantOrderPage("""
			{"elements": []}
			""");
		MPResultsResourcesPage<Payment> fallbackPage = paymentSearchPage();
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		when(payments.search(any(), any(MPRequestOptions.class)))
			.thenReturn(fallbackPage);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());

		assertThat(gateway.findPaymentForPreference(
			credential(), "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS")).isEmpty();
		assertThat(page.getTotal()).isZero();
		assertThat(page.getNextOffset()).isZero();
	}

	@Test
	void fallsBackToPaymentsSearchWhenMerchantOrdersCollectionIsNull() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		MPElementsResourcesPage<MerchantOrder> page = merchantOrderPage("""
			{"next_offset": 0, "total": 0}
			""");
		Payment payment = providerPayment(998877L, "approved");
		MerchantOrder linkedOrder = linkedOrder("pref-123");
		MPResultsResourcesPage<Payment> fallbackPage = paymentSearchPage(payment);
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(page);
		when(payments.search(any(), any(MPRequestOptions.class)))
			.thenReturn(fallbackPage);
		when(payments.get(
			org.mockito.ArgumentMatchers.eq(998877L), any(MPRequestOptions.class)))
			.thenReturn(payment);
		when(merchantOrders.get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(linkedOrder);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());

		VerifiedProviderPayment result = gateway.findPaymentForPreference(
			credential(), "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS").orElseThrow();

		assertThat(result.status().name()).isEqualTo("APPROVED");
		ArgumentCaptor<MPSearchRequest> request = ArgumentCaptor.forClass(MPSearchRequest.class);
		ArgumentCaptor<MPRequestOptions> options =
			ArgumentCaptor.forClass(MPRequestOptions.class);
		verify(payments).search(request.capture(), options.capture());
		assertThat(request.getValue().getFilters()).containsEntry(
			"external_reference", "external-123");
		assertThat(request.getValue().getFilters()).containsEntry("sort", "date_created");
		assertThat(request.getValue().getFilters()).containsEntry("criteria", "desc");
		assertThat(options.getValue().getAccessToken()).isEqualTo("TEST-secret-token");
	}

	@Test
	void reportsWhenFallbackPaymentHasNoOrder() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.getOrder()).thenReturn(null);
		FallbackFixture fixture = fallbackFixture(payment);

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PREFERENCE_NOT_VERIFIABLE");
		assertThat(failure.reconciliationDiagnostics().preferenceLinkDiagnostics())
			.satisfies(diagnostics -> {
				assertThat(diagnostics.paymentOrderPresent()).isFalse();
				assertThat(diagnostics.paymentOrderIdPresent()).isFalse();
				assertThat(diagnostics.paymentOrderTypePresent()).isFalse();
				assertThat(diagnostics.merchantOrderLookupAttempted()).isFalse();
				assertThat(diagnostics.merchantOrderResponsePresent()).isFalse();
				assertThat(diagnostics.merchantOrderPreferencePresent()).isFalse();
				assertThat(diagnostics.merchantOrderPreferenceMatches()).isNull();
			});
		verify(fixture.merchantOrders(), never()).get(any(), any(MPRequestOptions.class));
	}

	@Test
	void reportsWhenFallbackPaymentOrderHasNoId() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.getOrder().getId()).thenReturn(null);
		FallbackFixture fixture = fallbackFixture(payment);

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PREFERENCE_NOT_VERIFIABLE");
		assertThat(failure.reconciliationDiagnostics().preferenceLinkDiagnostics())
			.satisfies(diagnostics -> {
				assertThat(diagnostics.paymentOrderPresent()).isTrue();
				assertThat(diagnostics.paymentOrderIdPresent()).isFalse();
				assertThat(diagnostics.paymentOrderTypePresent()).isTrue();
				assertThat(diagnostics.merchantOrderLookupAttempted()).isFalse();
			});
		verify(fixture.merchantOrders(), never()).get(any(), any(MPRequestOptions.class));
	}

	@Test
	void reportsMerchantOrderLookupErrorSeparately() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		FallbackFixture fixture = fallbackFixture(payment);
		MPApiException providerFailure = new MPApiException(
			"merchant order unavailable",
			new MPResponse(404, Map.of(), "{\"error\":\"not_found\"}"));
		when(fixture.merchantOrders().get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenThrow(providerFailure);

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PAYMENT_LOOKUP_FAILED");
		assertThat(failure.reconciliationDiagnostics().preferenceLinkDiagnostics())
			.satisfies(diagnostics -> {
				assertThat(diagnostics.paymentOrderPresent()).isTrue();
				assertThat(diagnostics.paymentOrderIdPresent()).isTrue();
				assertThat(diagnostics.paymentOrderTypePresent()).isTrue();
				assertThat(diagnostics.merchantOrderLookupAttempted()).isTrue();
				assertThat(diagnostics.merchantOrderResponsePresent()).isFalse();
				assertThat(diagnostics.merchantOrderHttpStatus()).isEqualTo(404);
				assertThat(diagnostics.merchantOrderProviderErrorCode()).isEqualTo("not_found");
			});
	}

	@Test
	void reportsWhenMerchantOrderResponseIsNull() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		FallbackFixture fixture = fallbackFixture(payment);
		when(fixture.merchantOrders().get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(null);

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PREFERENCE_NOT_VERIFIABLE");
		assertThat(failure.reconciliationDiagnostics().preferenceLinkDiagnostics())
			.satisfies(diagnostics -> {
				assertThat(diagnostics.merchantOrderLookupAttempted()).isTrue();
				assertThat(diagnostics.merchantOrderResponsePresent()).isFalse();
				assertThat(diagnostics.merchantOrderPreferencePresent()).isFalse();
				assertThat(diagnostics.merchantOrderPreferenceMatches()).isNull();
			});
	}

	@Test
	void reportsWhenMerchantOrderPreferenceIsNull() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		FallbackFixture fixture = fallbackFixture(payment);
		MerchantOrder orderWithoutPreference = linkedOrder(null);
		when(fixture.merchantOrders().get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(orderWithoutPreference);

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PREFERENCE_NOT_VERIFIABLE");
		assertThat(failure.reconciliationDiagnostics().preferenceLinkDiagnostics())
			.satisfies(diagnostics -> {
				assertThat(diagnostics.merchantOrderLookupAttempted()).isTrue();
				assertThat(diagnostics.merchantOrderResponsePresent()).isTrue();
				assertThat(diagnostics.merchantOrderPreferencePresent()).isFalse();
				assertThat(diagnostics.merchantOrderPreferenceMatches()).isNull();
			});
	}

	@Test
	void rejectsFallbackPaymentWhenPreferenceCannotBeVerified() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		FallbackFixture fixture = fallbackFixture(payment);
		MerchantOrder wrongPreference = linkedOrder("pref-other");
		MPElementsResourcesPage<PreferenceSearch> preferencePage =
			preferenceSearchPage("pref-123", "pref-other");
		when(fixture.merchantOrders().get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(wrongPreference);
		when(fixture.preferences().search(any(), any(MPRequestOptions.class)))
			.thenReturn(preferencePage);

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PREFERENCE_NOT_VERIFIABLE");
		assertThat(failure.reconciliationDiagnostics().preferenceLinkDiagnostics())
			.satisfies(diagnostics -> {
				assertThat(diagnostics.merchantOrderResponsePresent()).isTrue();
				assertThat(diagnostics.merchantOrderPreferencePresent()).isTrue();
				assertThat(diagnostics.merchantOrderPreferenceMatches()).isFalse();
			});
		assertThat(failure.reconciliationDiagnostics().preferenceSearchDiagnostics())
			.isEqualTo(new PreferenceSearchDiagnostics(
				2, true, true, false, true, true));
	}

	@Test
	void rejectsFallbackPaymentFromAnotherSeller() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.getCollectorId()).thenReturn(999999L);
		FallbackFixture fixture = fallbackFixture(payment);

		assertFallbackFailure(fixture.gateway(), credential(), "SELLER_MISMATCH");
	}

	@Test
	void rejectsFallbackPaymentFromTheWrongEnvironment() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.isLiveMode()).thenReturn(false);
		FallbackFixture fixture = fallbackFixture(payment);
		PaymentCredential production = new PaymentCredential(
			"PROD-secret-token", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);

		assertFallbackFailure(fixture.gateway(), production, "ENVIRONMENT_MISMATCH");
	}

	@Test
	void rejectsFallbackPaymentWithDifferentAmount() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.getTransactionAmount()).thenReturn(new BigDecimal("6.00"));
		FallbackFixture fixture = fallbackFixture(payment);

		assertFallbackFailure(fixture.gateway(), credential(), "AMOUNT_MISMATCH");
	}

	@Test
	void rejectsFallbackPaymentWithDifferentCurrency() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.getCurrencyId()).thenReturn("USD");
		FallbackFixture fixture = fallbackFixture(payment);

		assertFallbackFailure(fixture.gateway(), credential(), "CURRENCY_MISMATCH");
	}

	@Test
	void rejectsFallbackPaymentWhoseLoadedExternalReferenceDoesNotMatch() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		when(payment.getExternalReference()).thenReturn("external-other");
		FallbackFixture fixture = fallbackFixture(payment);

		assertFallbackFailure(fixture.gateway(), credential(), "REFERENCE_MISMATCH");
	}

	@Test
	void selectsAValidApprovedFallbackPaymentOverAnOlderRejection() throws Exception {
		Payment rejected = providerPayment(1001L, "rejected");
		when(rejected.getDateLastUpdated())
			.thenReturn(OffsetDateTime.parse("2026-08-01T10:00:00Z"));
		Payment approved = providerPayment(1002L, "approved");
		when(approved.getDateLastUpdated())
			.thenReturn(OffsetDateTime.parse("2026-08-01T11:00:00Z"));
		FallbackFixture fixture = fallbackFixture(rejected, approved);

		VerifiedProviderPayment result = fixture.gateway().findPaymentForPreference(
			credential(), "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS").orElseThrow();

		assertThat(result.providerPaymentId()).isEqualTo("1002");
		assertThat(result.status().name()).isEqualTo("APPROVED");
	}

	@Test
	void selectsTheNewestValidFallbackPaymentWhenNoneIsApproved() throws Exception {
		Payment pending = providerPayment(1001L, "pending");
		when(pending.getDateLastUpdated())
			.thenReturn(OffsetDateTime.parse("2026-08-01T10:00:00Z"));
		Payment rejected = providerPayment(1002L, "rejected");
		when(rejected.getDateLastUpdated())
			.thenReturn(OffsetDateTime.parse("2026-08-01T11:00:00Z"));
		FallbackFixture fixture = fallbackFixture(pending, rejected);

		VerifiedProviderPayment result = fixture.gateway().findPaymentForPreference(
			credential(), "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS").orElseThrow();

		assertThat(result.providerPaymentId()).isEqualTo("1002");
		assertThat(result.status().name()).isEqualTo("REJECTED");
	}

	@Test
	void reportsAnInvalidPaymentsSearchResponseWithoutExposingItsPayload() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		MPElementsResourcesPage<MerchantOrder> primary = merchantOrderPage("""
			{"next_offset": 0, "total": 0}
			""");
		@SuppressWarnings("unchecked")
		MPResultsResourcesPage<Payment> invalidFallback = mock(MPResultsResourcesPage.class);
		when(invalidFallback.getResults()).thenReturn(null);
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(primary);
		when(payments.search(any(), any(MPRequestOptions.class))).thenReturn(invalidFallback);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, payments, merchantOrders, properties());

		assertThatThrownBy(() -> gateway.findPaymentForPreference(
			credential(), "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS"))
			.isInstanceOf(CheckoutPaymentException.class)
			.satisfies(exception -> {
				CheckoutPaymentException failure = (CheckoutPaymentException) exception;
				assertThat(failure.reconciliationDiagnostics().stage())
					.isEqualTo("PROVIDER_SEARCH");
				assertThat(failure.reconciliationDiagnostics().reason())
					.isEqualTo("INVALID_PROVIDER_RESPONSE");
			});
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
			credential, "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS"))
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
			credential, "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS"))
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
			credential, "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS").orElseThrow();

		assertThat(result.providerPaymentId()).isEqualTo("1002");
		assertThat(result.status().name()).isEqualTo("APPROVED");
	}

	@Test
	void diagnosesOneStoredPreferenceThatMatchesTheActualPaymentPreference()
			throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		MPElementsResourcesPage<PreferenceSearch> page =
			preferenceSearchPage("pref-stored");
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());
		PaymentCredential production = new PaymentCredential(
			"synthetic-production-token", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			production, "external-123", "pref-stored", "pref-stored");

		assertThat(diagnostics).isEqualTo(new PreferenceSearchDiagnostics(
			1, true, true, true, false, true));
		ArgumentCaptor<MPSearchRequest> request = ArgumentCaptor.forClass(MPSearchRequest.class);
		ArgumentCaptor<MPRequestOptions> options =
			ArgumentCaptor.forClass(MPRequestOptions.class);
		verify(preferences).search(request.capture(), options.capture());
		assertThat(request.getValue().getFilters())
			.containsOnlyKeys("external_reference")
			.containsEntry("external_reference", "external-123");
		assertThat(options.getValue().getAccessToken()).isEqualTo("synthetic-production-token");
	}

	@Test
	void diagnosesTwoDistinctStoredAndActualPaymentPreferences() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		MPElementsResourcesPage<PreferenceSearch> page =
			preferenceSearchPage("pref-stored", "pref-actual");
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			credential(), "external-123", "pref-stored", "pref-actual");

		assertThat(diagnostics).isEqualTo(new PreferenceSearchDiagnostics(
			2, true, true, false, true, true));
	}

	@Test
	void diagnosesWhenTheStoredPreferenceIsAbsent() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		MPElementsResourcesPage<PreferenceSearch> page =
			preferenceSearchPage("pref-actual");
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			credential(), "external-123", "pref-stored", "pref-actual");

		assertThat(diagnostics).isEqualTo(new PreferenceSearchDiagnostics(
			1, false, true, false, false, false));
	}

	@Test
	void diagnosesWhenTheActualPaymentPreferenceIsAbsent() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		MPElementsResourcesPage<PreferenceSearch> page =
			preferenceSearchPage("pref-stored");
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			credential(), "external-123", "pref-stored", "pref-actual");

		assertThat(diagnostics).isEqualTo(new PreferenceSearchDiagnostics(
			1, true, false, false, false, true));
	}

	@Test
	void diagnosesWhenThePaymentDoesNotExposeAnActualPreference() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		MPElementsResourcesPage<PreferenceSearch> page =
			preferenceSearchPage("pref-stored");
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			credential(), "external-123", "pref-stored", null);

		assertThat(diagnostics).isEqualTo(new PreferenceSearchDiagnostics(
			1, true, false, false, false, true));
	}

	@Test
	void diagnosesAnEmptyPreferenceSearchResponse() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		MPElementsResourcesPage<PreferenceSearch> page = preferenceSearchPage();
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenReturn(page);
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			credential(), "external-123", "pref-stored", "pref-actual");

		assertThat(diagnostics).isEqualTo(new PreferenceSearchDiagnostics(
			0, false, false, false, false, false));
	}

	@Test
	void keepsPreferenceValidationUnchangedWhenPreferenceSearchFails() throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		when(preferences.search(any(), any(MPRequestOptions.class)))
			.thenThrow(new MPException("synthetic provider failure"));
		MercadoPagoCheckoutProGateway gateway = new MercadoPagoCheckoutProGateway(
			preferences, mock(PaymentClient.class), mock(MerchantOrderClient.class), properties());

		PreferenceSearchDiagnostics diagnostics = gateway.diagnosePreferenceHistory(
			credential(), "external-123", "pref-stored", "pref-actual");

		assertThat(diagnostics).isEqualTo(PreferenceSearchDiagnostics.unavailable(false));
	}

	@Test
	void keepsTheOriginalMismatchWhenDiagnosticPreferenceSearchFails() throws Exception {
		Payment payment = providerPayment(1001L, "approved");
		FallbackFixture fixture = fallbackFixture(payment);
		MerchantOrder mismatchedOrder = linkedOrder("pref-other");
		when(fixture.merchantOrders().get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(mismatchedOrder);
		when(fixture.preferences().search(any(), any(MPRequestOptions.class)))
			.thenThrow(new MPException("synthetic provider failure"));

		CheckoutPaymentException failure = fallbackFailure(fixture.gateway(), credential());

		assertThat(failure.reconciliationDiagnostics().reason())
			.isEqualTo("PREFERENCE_NOT_VERIFIABLE");
		assertThat(failure.reconciliationDiagnostics().preferenceSearchDiagnostics())
			.isEqualTo(PreferenceSearchDiagnostics.unavailable(false));
	}

	private Payment providerPayment(long id, String status) {
		Payment payment = mock(Payment.class);
		PaymentOrder paymentOrder = mock(PaymentOrder.class);
		when(payment.getId()).thenReturn(id);
		when(payment.getCollectorId()).thenReturn(123456L);
		when(payment.getOrder()).thenReturn(paymentOrder);
		when(paymentOrder.getId()).thenReturn(77L);
		when(paymentOrder.getType()).thenReturn("merchant_order");
		when(payment.getExternalReference()).thenReturn("external-123");
		when(payment.getTransactionAmount()).thenReturn(new BigDecimal("5.00"));
		when(payment.getCurrencyId()).thenReturn("ARS");
		when(payment.isLiveMode()).thenReturn(true);
		when(payment.getStatus()).thenReturn(status);
		when(payment.getDateLastUpdated())
			.thenReturn(OffsetDateTime.parse("2026-08-01T10:00:00Z"));
		return payment;
	}

	@SuppressWarnings("unchecked")
	private MPElementsResourcesPage<PreferenceSearch> preferenceSearchPage(String... ids) {
		MPElementsResourcesPage<PreferenceSearch> page = mock(MPElementsResourcesPage.class);
		List<PreferenceSearch> elements = java.util.Arrays.stream(ids)
			.map(id -> {
				PreferenceSearch preference = mock(PreferenceSearch.class);
				when(preference.getId()).thenReturn(id);
				when(preference.getExternalReference()).thenReturn("external-123");
				return preference;
			})
			.toList();
		when(page.getElements()).thenReturn(elements);
		when(page.getTotal()).thenReturn(elements.size());
		when(page.getNextOffset()).thenReturn(elements.size());
		return page;
	}

	private MerchantOrder linkedOrder(String preferenceId) {
		MerchantOrder order = mock(MerchantOrder.class);
		when(order.getPreferenceId()).thenReturn(preferenceId);
		return order;
	}

	@SuppressWarnings("unchecked")
	private MPResultsResourcesPage<Payment> paymentSearchPage(Payment... results) {
		MPResultsResourcesPage<Payment> page = mock(MPResultsResourcesPage.class);
		when(page.getResults()).thenReturn(List.of(results));
		return page;
	}

	private FallbackFixture fallbackFixture(Payment... candidates) throws Exception {
		PreferenceClient preferences = mock(PreferenceClient.class);
		PaymentClient payments = mock(PaymentClient.class);
		MerchantOrderClient merchantOrders = mock(MerchantOrderClient.class);
		MPElementsResourcesPage<MerchantOrder> primary = merchantOrderPage("""
			{"next_offset": 0, "total": 0}
			""");
		MPResultsResourcesPage<Payment> fallbackPage = paymentSearchPage(candidates);
		when(merchantOrders.search(any(), any(MPRequestOptions.class))).thenReturn(primary);
		when(payments.search(any(), any(MPRequestOptions.class)))
			.thenReturn(fallbackPage);
		for (Payment candidate : candidates) {
			when(payments.get(
				org.mockito.ArgumentMatchers.eq(candidate.getId()),
				any(MPRequestOptions.class)))
				.thenReturn(candidate);
		}
		MerchantOrder defaultLinkedOrder = linkedOrder("pref-123");
		when(merchantOrders.get(
			org.mockito.ArgumentMatchers.eq(77L), any(MPRequestOptions.class)))
			.thenReturn(defaultLinkedOrder);
		return new FallbackFixture(
			new MercadoPagoCheckoutProGateway(
				preferences, payments, merchantOrders, properties()),
			preferences, payments, merchantOrders);
	}

	private void assertFallbackFailure(
			MercadoPagoCheckoutProGateway gateway, PaymentCredential credential,
			String reason) {
		assertThat(fallbackFailure(gateway, credential).reconciliationDiagnostics().reason())
			.isEqualTo(reason);
	}

	private CheckoutPaymentException fallbackFailure(
			MercadoPagoCheckoutProGateway gateway, PaymentCredential credential) {
		return org.assertj.core.api.Assertions.catchThrowableOfType(
			() -> gateway.findPaymentForPreference(
			credential, "pref-123", "external-123",
			new BigDecimal("5.00"), "ARS"),
			CheckoutPaymentException.class);
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"TEST-secret-token", "123456", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
	}

	private MPElementsResourcesPage<MerchantOrder> merchantOrderPage(String json)
			throws Exception {
		Type type = new TypeToken<MPElementsResourcesPage<MerchantOrder>>() { }.getType();
		return Serializer.deserializeElementsResourcesPageFromJson(type, json);
	}

	private CheckoutProProperties properties() {
		return new CheckoutProProperties(
			true, "TEST-token", "123456", "demo",
			URI.create("https://api.example.test"), URI.create("https://shop.example.test"),
			"webhook-secret", Duration.ofSeconds(1), Duration.ofSeconds(2),
			Duration.ofSeconds(30), Duration.ofSeconds(30), 3,
			Duration.ofHours(24));
	}

	private record FallbackFixture(
		MercadoPagoCheckoutProGateway gateway,
		PreferenceClient preferences,
		PaymentClient payments,
		MerchantOrderClient merchantOrders) {
	}
}
