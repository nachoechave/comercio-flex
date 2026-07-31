package com.comercioflex.payment.infrastructure.mercadopago;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.comercioflex.payment.application.CheckoutPaymentException;
import com.comercioflex.payment.application.CheckoutProGateway;
import com.comercioflex.payment.application.CheckoutProProperties;
import com.comercioflex.payment.application.PaymentOAuthProperties;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CheckoutProProperties.class)
public class CheckoutProConfiguration {

	@Bean
	CheckoutProGateway checkoutProGateway(
			CheckoutProProperties properties, PaymentOAuthProperties oauthProperties) {
		validate(properties, oauthProperties);
		return new MercadoPagoCheckoutProGateway(properties);
	}

	private void validate(
			CheckoutProProperties properties, PaymentOAuthProperties oauthProperties) {
		if (!properties.enabled()) {
			return;
		}
		if (properties.publicBackendBaseUri() == null
				|| properties.frontendBaseUri() == null
				|| blank(properties.webhookSecret())
				|| !positive(properties.connectTimeout())
				|| !positive(properties.readTimeout())
				|| !positive(properties.webhookLease())
				|| !positive(properties.retryDelay())
				|| !positive(properties.returnTokenTtl())) {
			throw invalid("La configuración de Checkout Pro está incompleta.");
		}
		requireHttps(properties.publicBackendBaseUri());
		requireHttps(properties.frontendBaseUri());
		if (oauthProperties.environment() == PaymentEnvironment.PRODUCTION) {
			if (!blank(properties.testAccessToken())
					|| !blank(properties.testSellerAccountId())
					|| !blank(properties.testDemoTenantSlug())) {
				throw invalid("Producción no admite credenciales centrales de prueba.");
			}
			if (!oauthProperties.enabled()) {
				throw invalid("Producción requiere OAuth por comercio habilitado.");
			}
		}
		else if (blank(properties.testAccessToken())
				|| blank(properties.testSellerAccountId())
				|| blank(properties.testDemoTenantSlug())) {
			throw invalid("TEST requiere una cuenta central limitada al tenant demo.");
		}
	}

	private void requireHttps(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw invalid("Checkout Pro requiere URLs públicas HTTPS.");
		}
	}

	private boolean positive(java.time.Duration duration) {
		return duration != null && !duration.isZero() && !duration.isNegative();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private CheckoutPaymentException invalid(String message) {
		return new CheckoutPaymentException("PAYMENT_CONFIGURATION_INVALID", message);
	}
}
