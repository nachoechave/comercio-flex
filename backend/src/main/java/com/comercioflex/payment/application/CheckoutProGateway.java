package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface CheckoutProGateway {

	CreatedCheckoutPreference createPreference(
		PaymentCredential credential,
		CheckoutPreferenceCommand command);

	VerifiedProviderPayment findPayment(
		PaymentCredential credential,
		String providerPaymentId);

	Optional<VerifiedProviderPayment> findPaymentForPreference(
		PaymentCredential credential,
		String preferenceId,
		String externalReference,
		BigDecimal amount,
		String currencyCode);

	PreferenceSearchDiagnostics diagnosePreferenceHistory(
		PaymentCredential credential,
		String externalReference,
		String storedPreferenceId,
		String actualPaymentPreferenceId);

	ProviderCheckoutState inspectPreference(
		PaymentCredential credential,
		String preferenceId,
		String externalReference);
}
