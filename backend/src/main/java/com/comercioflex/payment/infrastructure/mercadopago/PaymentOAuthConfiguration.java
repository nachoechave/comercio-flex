package com.comercioflex.payment.infrastructure.mercadopago;

import java.security.SecureRandom;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.comercioflex.payment.application.CredentialCipher;
import com.comercioflex.payment.application.MerchantOAuthClient;
import com.comercioflex.payment.application.PaymentOAuthException;
import com.comercioflex.payment.application.PaymentOAuthProperties;
import com.comercioflex.payment.infrastructure.crypto.AesGcmCredentialCipher;

@Configuration
@EnableConfigurationProperties(PaymentOAuthProperties.class)
public class PaymentOAuthConfiguration {

	@Bean
	CredentialCipher paymentCredentialCipher(PaymentOAuthProperties properties) {
		validate(properties);
		SecretKey key;
		if (properties.enabled()) {
			if (properties.encryptionKey() == null || properties.encryptionKey().isBlank()) {
				throw new PaymentOAuthException(
					"PAYMENT_CONFIGURATION_INVALID",
					"Falta configurar la clave de cifrado de pagos.");
			}
			key = AesGcmCredentialCipher.decodeAes256Key(properties.encryptionKey());
		}
		else {
			byte[] ephemeral = new byte[32];
			new SecureRandom().nextBytes(ephemeral);
			key = new SecretKeySpec(ephemeral, "AES");
		}
		return new AesGcmCredentialCipher(
			properties.activeKeyId(), Map.of(properties.activeKeyId(), key));
	}

	@Bean
	MerchantOAuthClient merchantOAuthClient(PaymentOAuthProperties properties) {
		validate(properties);
		SimpleClientHttpRequestFactory requestFactory =
			new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		RestClient oauthClient = RestClient.builder()
			.baseUrl(properties.apiBaseUri().toString())
			.requestFactory(requestFactory)
			.build();
		RestClient identityClient = RestClient.builder()
			.baseUrl(properties.identityBaseUri().toString())
			.requestFactory(requestFactory)
			.build();
		return new MercadoPagoOAuthClientAdapter(
			oauthClient, identityClient, properties);
	}

	private void validate(PaymentOAuthProperties properties) {
		if (!properties.enabled()) {
			return;
		}
		if (blank(properties.clientId()) || blank(properties.clientSecret())
				|| properties.redirectUri() == null
				|| properties.frontendBaseUri() == null
				|| properties.connectTimeout().isNegative()
				|| properties.connectTimeout().isZero()
				|| properties.readTimeout().isNegative()
				|| properties.readTimeout().isZero()) {
			throw new PaymentOAuthException(
				"PAYMENT_CONFIGURATION_INVALID",
				"La configuración OAuth de Mercado Pago está incompleta.");
		}
		if (properties.environment() == com.comercioflex.payment.domain.PaymentEnvironment.PRODUCTION
				&& (!"https".equalsIgnoreCase(properties.redirectUri().getScheme())
					|| !"https".equalsIgnoreCase(properties.frontendBaseUri().getScheme()))) {
			throw new PaymentOAuthException(
				"PAYMENT_CONFIGURATION_INVALID",
				"Producción requiere URLs HTTPS para OAuth.");
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
