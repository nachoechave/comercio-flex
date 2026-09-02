package com.comercioflex.payment.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.comercioflex.payment.application.CreateQrOrderCommand;
import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.application.QrOrderException;
import com.comercioflex.payment.domain.PaymentEnvironment;

class MercadoPagoQrOrderGatewayAdapterTests {

	private static final UUID IDEMPOTENCY =
		UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private MockRestServiceServer server;
	private MercadoPagoQrOrderGatewayAdapter gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://api.mercadopago.test");
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new MercadoPagoQrOrderGatewayAdapter(builder.build());
	}

	@Test
	void createsADynamicOrderWithStableIdempotencyAndOAuthBearer() {
		server.expect(requestTo("https://api.mercadopago.test/v1/orders"))
			.andExpect(method(POST))
			.andExpect(header("Authorization", "Bearer oauth-access-token-fixture"))
			.andExpect(header("X-Idempotency-Key", IDEMPOTENCY.toString()))
			.andExpect(content().json("""
				{
				  "type":"qr","total_amount":"1250.00",
				  "external_reference":"cf_qr_reference",
				  "expiration_time":"PT29M55S",
				  "config":{"qr":{"external_pos_id":"CFP_OPAQUE","mode":"dynamic"}},
				  "transactions":{"payments":[{"amount":"1250.00"}]}
				}
				"""))
			.andRespond(withSuccess(createdResponse(), MediaType.APPLICATION_JSON));

		var result = gateway.createOrder(credential(), command());

		assertThat(result.orderId()).isEqualTo("ORD_PROVIDER_FIXTURE");
		assertThat(result.qrData()).isEqualTo("provider-qr-data-fixture");
		assertThat(result.externalPosId()).isEqualTo("CFP_OPAQUE");
		server.verify();
	}

	@Test
	void getsTheProviderOrderWithoutSendingIdempotencyAsQueryData() {
		server.expect(requestTo(
			"https://api.mercadopago.test/v1/orders/ORD_PROVIDER_FIXTURE"))
			.andExpect(method(GET))
			.andExpect(header("Authorization", "Bearer oauth-access-token-fixture"))
			.andRespond(withSuccess(processedResponse(), MediaType.APPLICATION_JSON));

		var result = gateway.getOrder(credential(), "ORD_PROVIDER_FIXTURE");

		assertThat(result.status()).isEqualTo("processed");
		assertThat(result.paymentStatus()).isEqualTo("approved");
		assertThat(result.paymentAmount()).isEqualByComparingTo("1250.00");
		server.verify();
	}

	@Test
	void rejectsAResponseWithoutNativeQrDataAndDoesNotExposeTheCredential() {
		server.expect(requestTo("https://api.mercadopago.test/v1/orders"))
			.andRespond(withSuccess(createdResponse().replace(
				"\"type_response\":{\"qr_data\":\"provider-qr-data-fixture\"}",
				"\"type_response\":{}"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> gateway.createOrder(credential(), command()))
			.isInstanceOfSatisfying(QrOrderException.class, exception -> {
				assertThat(exception.code()).isEqualTo("QR_PROVIDER_INVALID_RESPONSE");
				assertThat(exception.toString()).doesNotContain("oauth-access-token-fixture");
			});
	}

	private CreateQrOrderCommand command() {
		return new CreateQrOrderCommand(
			IDEMPOTENCY, "cf_qr_reference", "CFP_OPAQUE",
			new BigDecimal("1250.00"), "ARS", Duration.ofMinutes(29).plusSeconds(55));
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"oauth-access-token-fixture", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private String createdResponse() {
		return """
			{
			  "id":"ORD_PROVIDER_FIXTURE","type":"qr","status":"created",
			  "status_detail":"created","external_reference":"cf_qr_reference",
			  "total_amount":"1250.00","currency":"ARS","user_id":"123456",
			  "live_mode":true,
			  "config":{"qr":{"external_pos_id":"CFP_OPAQUE"}},
			  "transactions":{"payments":[{"amount":"1250.00"}]},
			  "type_response":{"qr_data":"provider-qr-data-fixture"}
			}
			""";
	}

	private String processedResponse() {
		return """
			{
			  "id":"ORD_PROVIDER_FIXTURE","type":"qr","status":"processed",
			  "status_detail":"accredited","external_reference":"cf_qr_reference",
			  "total_amount":"1250.00","currency":"ARS","user_id":"123456",
			  "live_mode":true,
			  "config":{"qr":{"external_pos_id":"CFP_OPAQUE"}},
			  "transactions":{"payments":[{
			    "id":"PAYMENT_PROVIDER_FIXTURE","status":"approved","amount":"1250.00"
			  }]}
			}
			""";
	}
}
