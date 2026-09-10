package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record CheckoutRoute(
	long internalId,
	UUID publicId,
	long tenantId,
	String tenantSlug,
	String tenantDatabaseKey,
	PaymentEnvironment environment,
	UUID paymentAttemptId,
	String expectedSellerAccountId,
	String preferenceId,
	String status,
	Instant expiresAt,
	String destinationType) {
	public CheckoutRoute(long internalId, UUID publicId, long tenantId, String tenantSlug, String tenantDatabaseKey, PaymentEnvironment environment, UUID paymentAttemptId, String expectedSellerAccountId, String preferenceId, String status, Instant expiresAt) {
		this(internalId,publicId,tenantId,tenantSlug,tenantDatabaseKey,environment,paymentAttemptId,expectedSellerAccountId,preferenceId,status,expiresAt,"ECOMMERCE_ORDER");
	}
	public boolean membership() { return "RADIO_MEMBERSHIP".equals(destinationType); }
}
