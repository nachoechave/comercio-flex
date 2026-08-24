package com.comercioflex.payment.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.payment.application.BankTransferInstructions;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.payment.domain.BankTransferStatus;

public record BankTransferPaymentResponse(
	UUID id,
	UUID orderId,
	String orderNumber,
	int attemptNumber,
	BankTransferStatus status,
	String bankName,
	String accountHolder,
	String alias,
	String cbuCvu,
	String amount,
	String currencyCode,
	Instant reservationExpiresAt,
	Instant receiptUploadedAt,
	String rejectionReason,
	boolean canUpload,
	Instant updatedAt
) {
	static BankTransferPaymentResponse from(BankTransferInstructions value) {
		BankTransferPayment payment = value.payment();
		return new BankTransferPaymentResponse(
			payment.id(), payment.orderId(), "ORD-%06d".formatted(payment.orderNumber()),
			payment.attemptNumber(), payment.status(), value.bankName(), value.accountHolder(),
			value.alias(), value.cbuCvu(), payment.amount().toPlainString(),
			payment.currencyCode(), payment.reservationExpiresAt(),
			payment.receiptUploadedAt(), payment.rejectionReason(), value.canUpload(),
			payment.updatedAt());
	}
}
