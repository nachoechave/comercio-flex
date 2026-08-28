package com.comercioflex.payment.infrastructure.mercadopago;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.comercioflex.payment.application.MerchantOAuthClient;
import com.comercioflex.payment.application.OAuthTokenResponse;
import com.comercioflex.payment.application.PaymentOAuthException;
import com.comercioflex.payment.application.PaymentOAuthProperties;
import com.comercioflex.payment.application.SellerAccountProfile;
import com.comercioflex.payment.domain.PaymentEnvironment;

public final class MercadoPagoOAuthClientAdapter implements MerchantOAuthClient {

	private static final String REQUIRED_SCOPE = "read write offline_access";

	private final RestClient oauthClient;
	private final RestClient identityClient;
	private final PaymentOAuthProperties properties;

	public MercadoPagoOAuthClientAdapter(
			RestClient oauthClient,
			RestClient identityClient,
			PaymentOAuthProperties properties) {
		this.oauthClient = oauthClient;
		this.identityClient = identityClient;
		this.properties = properties;
	}

	@Override
	public URI authorizationUri(String state, String codeChallenge) {
		return UriComponentsBuilder.fromUri(properties.authorizationBaseUri())
			.path("/authorization")
			.queryParam("client_id", properties.clientId())
			.queryParam("response_type", "code")
			.queryParam("platform_id", "mp")
			.queryParam("state", state)
			.queryParam("redirect_uri", properties.redirectUri())
			.queryParam("code_challenge", codeChallenge)
			.queryParam("code_challenge_method", "S256")
			.queryParam("scope", REQUIRED_SCOPE)
			.build()
			.encode()
			.toUri();
	}

	@Override
	public OAuthTokenResponse exchange(String code, String codeVerifier) {
		Map<String, Object> body = Map.of(
			"client_id", properties.clientId(),
			"client_secret", properties.clientSecret(),
			"grant_type", "authorization_code",
			"code", code,
			"code_verifier", codeVerifier,
			"redirect_uri", properties.redirectUri().toString(),
			"test_token", properties.environment() == PaymentEnvironment.TEST);
		return token(body);
	}

	@Override
	public OAuthTokenResponse refresh(String refreshToken) {
		return token(Map.of(
			"client_id", properties.clientId(),
			"client_secret", properties.clientSecret(),
			"grant_type", "refresh_token",
			"refresh_token", refreshToken,
			"scope", REQUIRED_SCOPE));
	}

	@Override
	public SellerAccountProfile fetchSellerProfile(String accessToken) {
		try {
			ProfileBody body = identityClient.get()
				.uri("/users/me")
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.body(ProfileBody.class);
			if (body == null || body.id() == null) {
				throw invalidResponse();
			}
			return new SellerAccountProfile(body.id().toString(), body.nickname());
		}
		catch (PaymentOAuthException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new PaymentOAuthException(
				"SELLER_PROFILE_UNAVAILABLE",
				"No se pudo verificar la cuenta de Mercado Pago.",
				exception);
		}
	}

	@Override
	public PaymentEnvironment environment() {
		return properties.environment();
	}

	private OAuthTokenResponse token(Map<String, Object> request) {
		try {
			TokenBody body = oauthClient.post()
				.uri("/oauth/token")
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(TokenBody.class);
			if (body == null
					|| body.accessToken() == null
					|| body.refreshToken() == null
					|| body.userId() == null
					|| body.expiresIn() == null
					|| body.expiresIn() <= 0) {
				throw invalidResponse();
			}
			Set<String> scopes = new LinkedHashSet<>();
			if (body.scope() != null) {
				Arrays.stream(body.scope().trim().split("\\s+"))
					.filter(value -> !value.isBlank())
					.forEach(scopes::add);
			}
			return new OAuthTokenResponse(
				body.accessToken(), body.refreshToken(), body.tokenType(),
				Duration.ofSeconds(body.expiresIn()), Set.copyOf(scopes),
				body.userId().toString(), Boolean.TRUE.equals(body.liveMode()));
		}
		catch (PaymentOAuthException exception) {
			throw exception;
		}
		catch (HttpClientErrorException.BadRequest exception) {
			String grantType = String.valueOf(request.get("grant_type"));
			if ("refresh_token".equals(grantType)) {
				throw new PaymentOAuthException(
					"REFRESH_REJECTED",
					"Mercado Pago requiere volver a autorizar la cuenta.",
					exception);
			}
			throw new PaymentOAuthException(
				"OAUTH_AUTHORIZATION_REJECTED",
				"Mercado Pago rechazó la autorización.",
				exception);
		}
		catch (RuntimeException exception) {
			throw new PaymentOAuthException(
				"OAUTH_PROVIDER_UNAVAILABLE",
				"No se pudo completar la autorización con Mercado Pago.",
				exception);
		}
	}

	private PaymentOAuthException invalidResponse() {
		return new PaymentOAuthException(
			"INVALID_PROVIDER_RESPONSE",
			"Mercado Pago devolvió una respuesta incompleta.");
	}

	private record TokenBody(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("refresh_token") String refreshToken,
		@JsonProperty("token_type") String tokenType,
		@JsonProperty("expires_in") Long expiresIn,
		String scope,
		@JsonProperty("user_id") Long userId,
		@JsonProperty("live_mode") Boolean liveMode) {
	}

	private record ProfileBody(Long id, String nickname) {
	}
}
