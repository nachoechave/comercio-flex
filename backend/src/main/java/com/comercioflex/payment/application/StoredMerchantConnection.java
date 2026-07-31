package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.MerchantConnectionStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

public record StoredMerchantConnection(
	long internalId,
	UUID publicId,
	long tenantId,
	UUID tenantPublicId,
	PaymentEnvironment environment,
	MerchantConnectionStatus status,
	String sellerAccountId,
	String sellerNickname,
	EncryptedSecret accessToken,
	EncryptedSecret refreshToken,
	Instant accessTokenExpiresAt,
	Instant connectedAt,
	long version) {
}
