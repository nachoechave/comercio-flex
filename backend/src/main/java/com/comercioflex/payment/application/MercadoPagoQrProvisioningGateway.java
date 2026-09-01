package com.comercioflex.payment.application;

import java.util.Optional;
import java.util.UUID;

public interface MercadoPagoQrProvisioningGateway {

	Optional<QrProviderStore> findStore(
		PaymentCredential credential, String externalStoreId);

	Optional<QrProviderPos> findPos(
		PaymentCredential credential, String externalPosId);

	QrProviderStore createStore(
		PaymentCredential credential,
		String externalStoreId,
		QrStoreSetupCommand command);

	QrProviderPos createPos(
		PaymentCredential credential,
		String providerStoreId,
		String externalStoreId,
		String externalPosId,
		UUID idempotencyKey);
}
