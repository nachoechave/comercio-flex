package com.comercioflex.payment.application;

public interface PaymentReceiptStorage {
	void store(String key, byte[] bytes, String contentType);
	PaymentReceiptObject load(String key, String contentType);
	void delete(String key);
}
