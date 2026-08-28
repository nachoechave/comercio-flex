package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.comercioflex.payment.domain.PaymentEnvironment;

class PaymentCredentialResolverTests {

	private final MerchantPaymentConnectionService connections =
		mock(MerchantPaymentConnectionService.class);
	private final PaymentOAuthProperties oauthProperties = mock(PaymentOAuthProperties.class);
	private final CheckoutProProperties checkoutProperties = mock(CheckoutProProperties.class);
	private final PaymentCredentialResolver resolver = new PaymentCredentialResolver(
		connections, oauthProperties, checkoutProperties);

	@Test
	void resolvesEachProductionTenantWithItsOwnOAuthCredential() {
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		PaymentCredential sellerA = credential("token-a", "seller-a");
		PaymentCredential sellerB = credential("token-b", "seller-b");
		when(connections.requireActiveCredential(11L)).thenReturn(sellerA);
		when(connections.requireActiveCredential(22L)).thenReturn(sellerB);

		assertThat(resolver.resolve(11L, "tienda-a")).isSameAs(sellerA);
		assertThat(resolver.resolve(22L, "tienda-b")).isSameAs(sellerB);
		verify(connections).requireActiveCredential(11L);
		verify(connections).requireActiveCredential(22L);
		verify(checkoutProperties, never()).testAccessToken();
	}

	@Test
	void productionFailsClosedWithoutFallingBackToTheCentralTestCredential() {
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		when(checkoutProperties.testAccessToken()).thenReturn("central-test-token");
		when(connections.requireActiveCredential(11L)).thenThrow(new PaymentOAuthException(
			"PAYMENT_ACCOUNT_NOT_CONNECTED", "La cuenta no está conectada."));

		assertThatThrownBy(() -> resolver.resolve(11L, "tienda-a"))
			.isInstanceOf(PaymentOAuthException.class)
			.extracting(exception -> ((PaymentOAuthException) exception).code())
			.isEqualTo("PAYMENT_ACCOUNT_NOT_CONNECTED");
		verify(checkoutProperties, never()).testAccessToken();
	}

	@Test
	void testCredentialIsRestrictedToTheConfiguredDemoTenant() {
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.TEST);
		when(checkoutProperties.testDemoTenantSlug()).thenReturn("tiendademo");
		when(checkoutProperties.testAccessToken()).thenReturn("central-test-token");
		when(checkoutProperties.testSellerAccountId()).thenReturn("test-seller");

		PaymentCredential credential = resolver.resolve(7L, "tiendademo");

		assertThat(credential.accessToken()).isEqualTo("central-test-token");
		assertThat(credential.source()).isEqualTo(PaymentCredential.Source.CENTRAL_TEST);
		assertThat(resolver.isAvailable(7L, "tiendademo")).isTrue();
		assertThat(resolver.isAvailable(8L, "otra-tienda")).isFalse();
		assertThatThrownBy(() -> resolver.resolve(8L, "otra-tienda"))
			.isInstanceOf(CheckoutPaymentException.class)
			.extracting(exception -> ((CheckoutPaymentException) exception).code())
			.isEqualTo("TEST_CREDENTIAL_FORBIDDEN");
		verify(connections, never()).requireActiveCredential(7L);
	}

	@Test
	void productionAvailabilityReflectsOnlyTheTenantConnection() {
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		when(connections.isConnected(11L)).thenReturn(true);
		when(connections.isConnected(22L)).thenReturn(false);

		assertThat(resolver.isAvailable(11L, "tienda-a")).isTrue();
		assertThat(resolver.isAvailable(22L, "tienda-b")).isFalse();
	}

	private PaymentCredential credential(String token, String seller) {
		return new PaymentCredential(
			token, seller, PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}
}
