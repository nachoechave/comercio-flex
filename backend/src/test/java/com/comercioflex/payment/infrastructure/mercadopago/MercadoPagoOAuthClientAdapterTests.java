package com.comercioflex.payment.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.comercioflex.payment.application.OAuthTokenResponse;
import com.comercioflex.payment.application.PaymentOAuthException;
import com.comercioflex.payment.application.PaymentOAuthProperties;
import com.comercioflex.payment.application.SellerAccountProfile;
import com.comercioflex.payment.domain.PaymentEnvironment;

class MercadoPagoOAuthClientAdapterTests {

	private MockRestServiceServer oauthServer;
	private MockRestServiceServer identityServer;
	private MercadoPagoOAuthClientAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder oauthBuilder = RestClient.builder()
			.baseUrl("https://api.mercadopago.test");
		RestClient.Builder identityBuilder = RestClient.builder()
			.baseUrl("https://api.mercadolibre.test");
		oauthServer = MockRestServiceServer.bindTo(oauthBuilder).build();
		identityServer = MockRestServiceServer.bindTo(identityBuilder).build();
		adapter = new MercadoPagoOAuthClientAdapter(
			oauthBuilder.build(), identityBuilder.build(), properties());
	}

	@Test
	void buildsAuthorizationWithStateAndPkceWithoutExposingTheClientSecret() {
		URI uri = adapter.authorizationUri("random-state", "s256-challenge");

		assertThat(uri.getHost()).isEqualTo("auth.mercadopago.com");
		assertThat(uri.getQuery())
			.contains("state=random-state")
			.contains("code_challenge=s256-challenge")
			.contains("code_challenge_method=S256")
			.contains("redirect_uri=")
			.doesNotContain("client-secret");
	}

	@Test
	void exchangesTypedTokensAndReadsOnlyTheSellerPublicIdentity() {
		oauthServer.expect(requestTo("https://api.mercadopago.test/oauth/token"))
			.andExpect(method(POST))
			.andRespond(withSuccess("""
				{
				  "access_token":"access-token",
				  "refresh_token":"refresh-token",
				  "token_type":"Bearer",
				  "expires_in":21600,
				  "scope":"read write offline_access",
				  "user_id":123456789,
				  "live_mode":false
				}
				""", MediaType.APPLICATION_JSON));
		identityServer.expect(requestTo("https://api.mercadolibre.test/users/me"))
			.andExpect(method(GET))
			.andExpect(header("Authorization", "Bearer access-token"))
			.andRespond(withSuccess("""
				{"id":123456789,"nickname":"CARNES_DEL_SUR","email":"not-copied@example.test"}
				""", MediaType.APPLICATION_JSON));

		OAuthTokenResponse token = adapter.exchange("authorization-code", "verifier");
		SellerAccountProfile profile = adapter.fetchSellerProfile(token.accessToken());

		assertThat(token.sellerAccountId()).isEqualTo("123456789");
		assertThat(token.scopes()).containsExactlyInAnyOrder("read", "write", "offline_access");
		assertThat(profile).isEqualTo(new SellerAccountProfile("123456789", "CARNES_DEL_SUR"));
		oauthServer.verify();
		identityServer.verify();
	}

	@Test
	void distinguishesARejectedRefreshFromATransientProviderFailure() {
		oauthServer.expect(requestTo("https://api.mercadopago.test/oauth/token"))
			.andExpect(method(POST))
			.andRespond(withBadRequest());

		assertThatThrownBy(() -> adapter.refresh("expired-refresh-token"))
			.isInstanceOf(PaymentOAuthException.class)
			.extracting(exception -> ((PaymentOAuthException) exception).code())
			.isEqualTo("REFRESH_REJECTED");
	}

	private PaymentOAuthProperties properties() {
		return new PaymentOAuthProperties(
			true,
			PaymentEnvironment.TEST,
			"client-id",
			"client-secret",
			URI.create("https://api.example.test/oauth/callback"),
			URI.create("https://auth.mercadopago.com"),
			URI.create("https://api.mercadopago.test"),
			URI.create("https://api.mercadolibre.test"),
			URI.create("https://app.example.test"),
			Duration.ofSeconds(3),
			Duration.ofSeconds(8),
			"v1",
			"unused");
	}
}
