package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface QrSetupRepository {

	QrSetupTenant requireActiveTenant(long tenantId, String tenantSlug);

	Optional<StoredQrSetup> find(long tenantId, PaymentEnvironment environment);

	StoredQrSetup createIfMissing(
		long tenantId,
		PaymentEnvironment environment,
		String externalStoreId,
		String externalPosId,
		UUID posIdempotencyKey,
		Instant now);

	boolean claimVerification(StoredQrSetup setup, Instant now, Instant staleBefore);

	void saveResult(
		StoredQrSetup setup,
		String providerStoreId,
		String providerPosId,
		QrProvisioningStatus status,
		QrAuthorizationStatus authorization,
		String safeErrorCode,
		Instant now);
}
