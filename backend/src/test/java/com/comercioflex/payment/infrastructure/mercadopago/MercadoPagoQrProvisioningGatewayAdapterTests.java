package com.comercioflex.payment.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.QrAuthorizationStatus;
import com.comercioflex.payment.application.QrProviderException;
import com.comercioflex.payment.application.QrStoreSetupCommand;
import com.comercioflex.payment.domain.PaymentEnvironment;

class MercadoPagoQrProvisioningGatewayAdapterTests {

	private MockRestServiceServer server;
	private MercadoPagoQrProvisioningGatewayAdapter gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://api.mercadopago.test");
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new MercadoPagoQrProvisioningGatewayAdapter(builder.build());
	}

	@Test
	void returnsEmptyWhenStoreSearchHasNoResults() {
		server.expect(requestTo(
			"https://api.mercadopago.test/users/123456/stores/search?external_id=CFSOPAQUE"))
			.andExpect(method(GET))
			.andExpect(header("Authorization", "Bearer access-token-fixture"))
			.andRespond(withSuccess("{\"paging\":{\"total\":0},\"results\":[]}",
				MediaType.APPLICATION_JSON));

		assertThat(gateway.findStore(credential(), "CFSOPAQUE")).isEmpty();
		server.verify();
	}

	@Test
	void parsesTheDocumentedWrappedStoreSearchResponse() {
		server.expect(requestTo(
			"https://api.mercadopago.test/users/123456/stores/search?external_id=CFSOPAQUE"))
			.andRespond(withSuccess("""
				[{"paging":{"total":1},"results":[
					{"id":30163646,"external_id":"CFSOPAQUE","name":"Sucursal"}
				]}]
				""", MediaType.APPLICATION_JSON));

		assertThat(gateway.findStore(credential(), "CFSOPAQUE"))
			.hasValueSatisfying(store -> {
				assertThat(store.providerId()).isEqualTo("30163646");
				assertThat(store.externalId()).isEqualTo("CFSOPAQUE");
			});
	}

	@Test
	void parsesAnActivePdvPosOwnedByTheOAuthSeller() {
		server.expect(requestTo(
			"https://api.mercadopago.test/v2/pos?external_id=CFPOPAQUE"))
			.andRespond(withSuccess("""
				{"paging":{"total":1},"results":[{
					"id":987,"external_id":"CFPOPAQUE","store_id":"30163646",
					"external_store_id":"CFSOPAQUE","user_id":123456,"status":"active",
					"config":{"qr":{"operating_mode":"pdv"}}
				}]}
				""", MediaType.APPLICATION_JSON));

		assertThat(gateway.findPos(credential(), "CFPOPAQUE"))
			.hasValueSatisfying(pos -> {
				assertThat(pos.providerId()).isEqualTo("987");
				assertThat(pos.sellerAccountId()).isEqualTo("123456");
				assertThat(pos.operatingMode()).isEqualTo("pdv");
			});
	}

	@Test
	void returnsEmptyWhenPosSearchHasNoResults() {
		server.expect(requestTo(
			"https://api.mercadopago.test/v2/pos?external_id=CFPOPAQUE"))
			.andExpect(method(GET))
			.andExpect(header("Authorization", "Bearer access-token-fixture"))
			.andRespond(withSuccess("{\"paging\":{\"total\":0},\"results\":[]}",
				MediaType.APPLICATION_JSON));

		assertThat(gateway.findPos(credential(), "CFPOPAQUE")).isEmpty();
		server.verify();
	}

	@Test
	void createsAStoreWithRealLocationAndNoCredentialInTheBody() {
		server.expect(requestTo("https://api.mercadopago.test/users/123456/stores"))
			.andExpect(method(POST))
			.andExpect(header("Authorization", "Bearer access-token-fixture"))
			.andExpect(content().json("""
				{
				  "name":"Sucursal Centro",
				  "external_id":"CFSOPAQUE",
				  "location":{
				    "street_name":"San Martin","street_number":"123",
				    "city_name":"Cordoba","state_name":"Cordoba",
				    "latitude":-31.4167,"longitude":-64.1833
				  }
				}
				"""))
			.andRespond(withSuccess(
				"{\"id\":30163646,\"external_id\":\"CFSOPAQUE\"}",
				MediaType.APPLICATION_JSON));

		gateway.createStore(credential(), "CFSOPAQUE", command());
		server.verify();
	}

	@Test
	void createsAPdvPosWithAStableIdempotencyHeader() {
		UUID key = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
		server.expect(requestTo("https://api.mercadopago.test/v2/pos"))
			.andExpect(method(POST))
			.andExpect(header("Authorization", "Bearer access-token-fixture"))
			.andExpect(header("X-Idempotency-Key", key.toString()))
			.andExpect(content().json("""
				{
				  "name":"Caja QR","store_id":"30163646",
				  "external_store_id":"CFSOPAQUE","external_id":"CFPOPAQUE",
				  "config":{"qr":{"operating_mode":"pdv"}}
				}
				"""))
			.andRespond(withSuccess("""
				{"id":987,"external_id":"CFPOPAQUE","store_id":"30163646",
				 "external_store_id":"CFSOPAQUE","user_id":123456,"status":"active",
				 "config":{"qr":{"operating_mode":"pdv"}}}
				""", MediaType.APPLICATION_JSON));

		gateway.createPos(
			credential(), "30163646", "CFSOPAQUE", "CFPOPAQUE", key);
		server.verify();
	}

	@Test
	void classifiesAuthorizationFailuresWithoutReturningProviderPayloads() {
		server.expect(requestTo(
			"https://api.mercadopago.test/v2/pos?external_id=CFPOPAQUE"))
			.andRespond(withForbiddenRequest());

		assertThatThrownBy(() -> gateway.findPos(credential(), "CFPOPAQUE"))
			.isInstanceOfSatisfying(QrProviderException.class, exception -> {
				assertThat(exception.category())
					.isEqualTo(QrAuthorizationStatus.UNAUTHORIZED_SCOPES);
				assertThat(exception.getMessage()).doesNotContain(
					"access-token-fixture", "CFPOPAQUE");
			});
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"access-token-fixture", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private QrStoreSetupCommand command() {
		return new QrStoreSetupCommand(
			"Sucursal Centro", "San Martin", "123", "Cordoba", "Cordoba",
			new BigDecimal("-31.4167"), new BigDecimal("-64.1833"), null);
	}
}
