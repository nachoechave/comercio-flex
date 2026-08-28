package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

class PaymentMethodsServiceTests {

	private final TenantResolver tenantResolver = mock(TenantResolver.class);
	private final CheckoutControlRepository controlRepository = mock(CheckoutControlRepository.class);
	private final BankTransferRepository bankTransferRepository = mock(BankTransferRepository.class);
	private final CheckoutProProperties checkoutProperties = mock(CheckoutProProperties.class);
	private final PaymentOAuthProperties oauthProperties = mock(PaymentOAuthProperties.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final TransactionTemplate controlTransactions = transactionTemplate();
	private final TransactionTemplate tenantTransactions = transactionTemplate();
	private final PaymentMethodsService service = new PaymentMethodsService(
		tenantResolver, controlRepository, bankTransferRepository,
		checkoutProperties, oauthProperties, credentials,
		controlTransactions, tenantTransactions);

	@BeforeEach
	void setUp() {
		when(tenantResolver.resolveActive("tienda-a"))
			.thenReturn(new ResolvedTenant(11L, "tienda-a", "Tienda A", "tenant-a"));
		when(checkoutProperties.enabled()).thenReturn(true);
		when(oauthProperties.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		when(controlRepository.isCommerciallyEnabled(11L, PaymentEnvironment.PRODUCTION))
			.thenReturn(true);
		when(bankTransferRepository.findConfiguration())
			.thenReturn(new BankTransferConfiguration(true, "Banco", "Tienda A", "TIENDA.A", null));
	}

	@Test
	void doesNotAdvertiseMercadoPagoWhenTheProductionTenantIsNotConnected() {
		when(credentials.isAvailable(11L, "tienda-a")).thenReturn(false);

		PaymentMethodsAvailability result = service.find("tienda-a");

		assertThat(result.mercadoPago()).isFalse();
		assertThat(result.bankTransfer()).isTrue();
	}

	@Test
	void advertisesMercadoPagoOnlyWhenCapabilityAndTenantCredentialAreAvailable() {
		when(credentials.isAvailable(11L, "tienda-a")).thenReturn(true);

		PaymentMethodsAvailability result = service.find("tienda-a");

		assertThat(result.mercadoPago()).isTrue();
		assertThat(result.bankTransfer()).isTrue();
	}

	@Test
	void commercialCapabilityStillFailsClosedBeforeCredentialLookup() {
		when(controlRepository.isCommerciallyEnabled(11L, PaymentEnvironment.PRODUCTION))
			.thenReturn(false);

		PaymentMethodsAvailability result = service.find("tienda-a");

		assertThat(result.mercadoPago()).isFalse();
		assertThat(result.bankTransfer()).isTrue();
		verify(credentials, never()).isAvailable(11L, "tienda-a");
	}

	@SuppressWarnings("unchecked")
	private static TransactionTemplate transactionTemplate() {
		TransactionTemplate template = mock(TransactionTemplate.class);
		when(template.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		return template;
	}
}
