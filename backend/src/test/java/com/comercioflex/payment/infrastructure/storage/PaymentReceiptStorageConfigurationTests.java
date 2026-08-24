package com.comercioflex.payment.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.comercioflex.media.config.ProductMediaConfiguration;
import com.comercioflex.media.config.ProductMediaProperties;
import com.comercioflex.payment.application.PaymentReceiptStorage;

class PaymentReceiptStorageConfigurationTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void selectsLocalStorage() {
		contextRunner()
			.withPropertyValues(
				"app.payments.receipt-storage.storage=local",
				"app.payments.receipt-storage.local-root=" + localRoot())
			.run(context -> {
				assertThat(context).hasSingleBean(PaymentReceiptStorage.class);
				assertThat(context).hasSingleBean(LocalPaymentReceiptStorage.class);
				assertThat(context).doesNotHaveBean(S3PaymentReceiptStorage.class);
			});
	}

	@Test
	void selectsS3StorageAndKeepsThePrivateObjectPrefix() {
		contextRunner()
			.withPropertyValues(validS3Properties())
			.run(context -> {
				assertThat(context).hasSingleBean(PaymentReceiptStorage.class);
				assertThat(context).hasSingleBean(S3PaymentReceiptStorage.class);
				assertThat(context).doesNotHaveBean(LocalPaymentReceiptStorage.class);
				assertThat(context.getBean(S3PaymentReceiptStorage.class)
					.objectKey("bank-transfer-receipts/tenant/order/random"))
					.isEqualTo("private/payment-receipts/"
						+ "bank-transfer-receipts/tenant/order/random");
			});
	}

	@Test
	void failsAtStartupWhenS3BucketIsMissing() {
		contextRunner()
			.withPropertyValues(
				"app.payments.receipt-storage.storage=s3",
				"app.payments.receipt-storage.s3.region=auto",
				"app.payments.receipt-storage.s3.access-key=receipt-access",
				"app.payments.receipt-storage.s3.secret-key=receipt-secret")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context).getFailure().hasRootCauseMessage(
					"PAYMENT_RECEIPT_S3_BUCKET is required when PAYMENT_RECEIPT_STORAGE=s3");
			});
	}

	@Test
	void failsAtStartupWhenS3CredentialsAreMissing() {
		contextRunner()
			.withPropertyValues(
				"app.payments.receipt-storage.storage=s3",
				"app.payments.receipt-storage.s3.bucket=receipt-bucket",
				"app.payments.receipt-storage.s3.region=auto")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context).getFailure().hasRootCauseMessage(
					"PAYMENT_RECEIPT_S3_ACCESS_KEY and PAYMENT_RECEIPT_S3_SECRET_KEY "
						+ "are required when PAYMENT_RECEIPT_STORAGE=s3");
			});
	}

	@Test
	void receiptBucketIsIndependentFromProductMediaBucket() {
		new ApplicationContextRunner()
			.withUserConfiguration(
				PaymentReceiptStorageConfiguration.class,
				ProductMediaConfiguration.class)
			.withPropertyValues(validS3Properties())
			.withPropertyValues("app.media.s3.bucket=image-bucket")
			.run(context -> {
				PaymentReceiptStorageProperties receipts = context.getBean(
					PaymentReceiptStorageProperties.class);
				ProductMediaProperties media = context.getBean(ProductMediaProperties.class);
				S3PaymentReceiptStorage storage = context.getBean(
					S3PaymentReceiptStorage.class);

				assertThat(receipts.getS3().getBucket()).isEqualTo("receipt-bucket");
				assertThat(storage.bucket()).isEqualTo("receipt-bucket");
				assertThat(media.getS3().getBucket()).isEqualTo("image-bucket");
			});
	}

	private ApplicationContextRunner contextRunner() {
		return new ApplicationContextRunner()
			.withUserConfiguration(PaymentReceiptStorageConfiguration.class);
	}

	private String[] validS3Properties() {
		return new String[] {
			"app.payments.receipt-storage.storage=s3",
			"app.payments.receipt-storage.s3.bucket=receipt-bucket",
			"app.payments.receipt-storage.s3.region=auto",
			"app.payments.receipt-storage.s3.endpoint=http://localhost:9000",
			"app.payments.receipt-storage.s3.access-key=receipt-access",
			"app.payments.receipt-storage.s3.secret-key=receipt-secret",
			"app.payments.receipt-storage.s3.path-style=false"
		};
	}

	private String localRoot() {
		return temporaryDirectory.resolve("receipts").toString().replace('\\', '/');
	}
}
