package com.comercioflex.payment.application;

public record BankTransferConfiguration(
	boolean enabled,
	String bankName,
	String accountHolder,
	String alias,
	String cbuCvu
) {
}
