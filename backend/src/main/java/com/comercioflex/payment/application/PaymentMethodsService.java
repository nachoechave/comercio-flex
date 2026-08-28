package com.comercioflex.payment.application;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

@Service
public class PaymentMethodsService {

	private final TenantResolver tenantResolver;
	private final CheckoutControlRepository controlRepository;
	private final BankTransferRepository bankTransferRepository;
	private final CheckoutProProperties checkoutProperties;
	private final PaymentOAuthProperties oauthProperties;
	private final PaymentCredentialResolver credentials;
	private final TransactionTemplate controlTransactions;
	private final TransactionTemplate tenantTransactions;

	public PaymentMethodsService(
			TenantResolver tenantResolver,
			CheckoutControlRepository controlRepository,
			BankTransferRepository bankTransferRepository,
			CheckoutProProperties checkoutProperties,
			PaymentOAuthProperties oauthProperties,
			PaymentCredentialResolver credentials,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate tenantTransactions) {
		this.tenantResolver = tenantResolver;
		this.controlRepository = controlRepository;
		this.bankTransferRepository = bankTransferRepository;
		this.checkoutProperties = checkoutProperties;
		this.oauthProperties = oauthProperties;
		this.credentials = credentials;
		this.controlTransactions = controlTransactions;
		this.tenantTransactions = tenantTransactions;
	}

	public PaymentMethodsAvailability find(String tenantSlug) {
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		boolean mercadoPago = checkoutProperties.enabled()
			&& Boolean.TRUE.equals(controlTransactions.execute(status ->
				controlRepository.isCommerciallyEnabled(
					tenant.id(), oauthProperties.environment())))
			&& credentials.isAvailable(tenant.id(), tenant.slug());
		boolean bankTransfer = Objects.requireNonNull(tenantTransactions.execute(status ->
			bankTransferRepository.findConfiguration())).enabled();
		return new PaymentMethodsAvailability(mercadoPago, bankTransfer);
	}
}
