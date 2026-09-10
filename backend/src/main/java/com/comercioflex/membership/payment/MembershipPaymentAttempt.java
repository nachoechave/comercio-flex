package com.comercioflex.membership.payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.comercioflex.payment.domain.PaymentEnvironment;
public record MembershipPaymentAttempt(long id, UUID publicId, long periodId, UUID periodPublicId,
 String idempotencyKey, byte[] fingerprint, String externalReference, PaymentEnvironment environment,
 String seller, String preferenceId, String checkoutUrl, String returnUrl, String technicalStatus,
 Instant expiresAt, Instant createdAt, Instant updatedAt, BigDecimal amount, String currency, String title) {}
