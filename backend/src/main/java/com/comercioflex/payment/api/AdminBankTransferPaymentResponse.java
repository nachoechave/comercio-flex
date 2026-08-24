package com.comercioflex.payment.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.payment.domain.BankTransferStatus;

public record AdminBankTransferPaymentResponse(
	UUID id,
	UUID orderId,
	String orderNumber,
	String customerName,
	String amount,
	String currencyCode,
	int attemptNumber,
	BankTransferStatus status,
	boolean receiptAvailable,
	String receiptOriginalFilename,
	String receiptContentType,
	Long receiptSize,
	Instant receiptUploadedAt,
	Instant reservationExpiresAt,
	Instant reviewedAt,
	String rejectionReason,
	Instant createdAt,
	Instant updatedAt
) {
	static AdminBankTransferPaymentResponse from(BankTransferPayment value) {
		return new AdminBankTransferPaymentResponse(
			value.id(), value.orderId(), "ORD-%06d".formatted(value.orderNumber()),
			value.customerName(), value.amount().toPlainString(), value.currencyCode(),
			value.attemptNumber(), value.status(), value.receiptObjectKey() != null,
			value.receiptOriginalFilename(), value.receiptContentType(), value.receiptSize(),
			value.receiptUploadedAt(), value.reservationExpiresAt(), value.reviewedAt(),
			value.rejectionReason(), value.createdAt(), value.updatedAt());
	}
}
