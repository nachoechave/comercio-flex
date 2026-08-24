package com.comercioflex.payment.application;

import java.time.Instant;

public record BankTransferInstructions(
	BankTransferPayment payment,
	String bankName,
	String accountHolder,
	String alias,
	String cbuCvu,
	boolean canUpload,
	Instant viewedAt
) {
}
