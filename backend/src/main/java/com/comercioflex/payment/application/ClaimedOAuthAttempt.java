package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record ClaimedOAuthAttempt(
	long internalId,
	UUID publicId,
	long tenantId,
	UUID tenantPublicId,
	String tenantSlug,
	long initiatedByUserId,
	UUID initiatedByUserPublicId,
	PaymentEnvironment environment,
	EncryptedSecret pkceVerifier,
	Instant expiresAt,
	long version) {
}
