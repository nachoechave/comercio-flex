package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.domain.BankTransferStatus;

public record BankTransferPayment(
	long internalId,
	UUID id,
	long orderInternalId,
	UUID orderId,
	long orderNumber,
	String customerName,
	BigDecimal amount,
	String currencyCode,
	Instant reservationExpiresAt,
	int attemptNumber,
	BankTransferStatus status,
	String receiptObjectKey,
	String receiptOriginalFilename,
	String receiptContentType,
	Long receiptSize,
	Instant receiptUploadedAt,
	Instant reviewedAt,
	Long reviewedBy,
	String rejectionReason,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public boolean canUpload(Instant now) {
		return status == BankTransferStatus.AWAITING_RECEIPT
			&& reservationExpiresAt.isAfter(now);
	}
}
