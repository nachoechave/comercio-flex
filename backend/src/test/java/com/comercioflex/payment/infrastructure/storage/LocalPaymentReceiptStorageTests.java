package com.comercioflex.payment.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comercioflex.payment.application.PaymentReceiptObject;
import com.comercioflex.payment.application.PaymentReceiptStorageException;

class LocalPaymentReceiptStorageTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void storesLoadsAndDeletesAReceipt() {
		LocalPaymentReceiptStorage storage = storage();
		String key = "bank-transfer-receipts/tenant-a/order-id/random-object-id";
		byte[] bytes = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

		storage.store(key, bytes, "application/pdf");
		PaymentReceiptObject loaded = storage.load(key, "application/pdf");

		assertThat(loaded.bytes()).isEqualTo(bytes);
		assertThat(loaded.contentType()).isEqualTo("application/pdf");
		storage.delete(key);
		assertThatThrownBy(() -> storage.load(key, "application/pdf"))
			.isInstanceOf(PaymentReceiptStorageException.class);
	}

	@Test
	void rejectsPathTraversal() {
		LocalPaymentReceiptStorage storage = storage();

		assertThatThrownBy(() -> storage.store("../outside.pdf", new byte[] {1},
			"application/pdf"))
			.isInstanceOf(PaymentReceiptStorageException.class)
			.hasMessageContaining("inválida");
	}

	private LocalPaymentReceiptStorage storage() {
		PaymentReceiptStorageProperties properties = new PaymentReceiptStorageProperties();
		properties.setLocalRoot(temporaryDirectory.resolve("receipts").toString());
		return new LocalPaymentReceiptStorage(properties);
	}
}
