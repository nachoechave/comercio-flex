package com.comercioflex.payment.application;

public record PaymentReceiptObject(byte[] bytes, String contentType) {
	public PaymentReceiptObject {
		bytes = bytes.clone();
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}
}
