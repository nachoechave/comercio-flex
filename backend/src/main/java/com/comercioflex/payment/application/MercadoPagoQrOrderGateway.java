package com.comercioflex.payment.application;

public interface MercadoPagoQrOrderGateway {

	ProviderQrOrder createOrder(PaymentCredential credential, CreateQrOrderCommand command);

	ProviderQrOrder getOrder(PaymentCredential credential, String providerOrderId);
}
