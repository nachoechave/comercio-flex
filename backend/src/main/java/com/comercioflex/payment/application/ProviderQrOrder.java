package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderQrOrder(
	String orderId,
	String type,
	String status,
	String statusDetail,
	String externalReference,
	BigDecimal totalAmount,
	String currencyCode,
	String sellerAccountId,
	Boolean liveMode,
	String externalPosId,
	String qrData,
	String paymentId,
	String paymentStatus,
	BigDecimal paymentAmount,
	Instant updatedAt) {
}
