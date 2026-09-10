package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.comercioflex.payment.domain.PaymentResultStatus;

public record VerifiedProviderPayment(
	String providerPaymentId,
	String sellerAccountId,
	String preferenceId,
	String externalReference,
	BigDecimal amount,
	String currencyCode,
	boolean liveMode,
	PaymentResultStatus status,
	Instant providerUpdatedAt,
 String rawStatus,
 Instant paidAt) {
 public VerifiedProviderPayment(String providerPaymentId,String sellerAccountId,String preferenceId,String externalReference,BigDecimal amount,String currencyCode,boolean liveMode,PaymentResultStatus status,Instant providerUpdatedAt) {
  this(providerPaymentId,sellerAccountId,preferenceId,externalReference,amount,currencyCode,liveMode,status,providerUpdatedAt,status.name(),status==PaymentResultStatus.APPROVED?providerUpdatedAt:null);
 }
}
