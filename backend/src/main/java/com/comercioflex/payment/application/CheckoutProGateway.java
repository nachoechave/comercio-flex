package com.comercioflex.payment.application;

public interface CheckoutProGateway {

	CreatedCheckoutPreference createPreference(
		PaymentCredential credential,
		CheckoutPreferenceCommand command);

	VerifiedProviderPayment findPayment(
		PaymentCredential credential,
		String providerPaymentId);
}
