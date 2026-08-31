package com.comercioflex.payment.application;

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
		String externalReference);

	ProviderCheckoutState inspectPreference(
		PaymentCredential credential,
		String preferenceId,
		String externalReference);
}
