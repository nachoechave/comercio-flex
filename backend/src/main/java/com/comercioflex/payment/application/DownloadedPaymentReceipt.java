package com.comercioflex.payment.application;

public record DownloadedPaymentReceipt(
	PaymentReceiptObject object,
	String originalFilename
) {
}
