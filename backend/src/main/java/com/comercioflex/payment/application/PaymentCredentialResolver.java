package com.comercioflex.payment.application;

import org.springframework.stereotype.Component;

import com.comercioflex.payment.domain.PaymentEnvironment;

@Component
public class PaymentCredentialResolver {

	private final MerchantPaymentConnectionService connections;
	private final PaymentOAuthProperties oauthProperties;
	private final CheckoutProProperties checkoutProperties;

	public PaymentCredentialResolver(
			MerchantPaymentConnectionService connections,
			PaymentOAuthProperties oauthProperties,
			CheckoutProProperties checkoutProperties) {
		this.connections = connections;
		this.oauthProperties = oauthProperties;
		this.checkoutProperties = checkoutProperties;
	}

	public PaymentCredential resolve(long tenantId, String tenantSlug) {
		if (oauthProperties.environment() == PaymentEnvironment.PRODUCTION) {
			return connections.requireActiveCredential(tenantId);
		}
		if (!tenantSlug.equals(checkoutProperties.testDemoTenantSlug())) {
			throw new CheckoutPaymentException(
				"TEST_CREDENTIAL_FORBIDDEN",
				"El cobro de prueba sólo está disponible para el comercio demo configurado.");
		}
		return new PaymentCredential(
			checkoutProperties.testAccessToken(),
			checkoutProperties.testSellerAccountId(),
			PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);
	}
}
