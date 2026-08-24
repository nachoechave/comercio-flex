package com.comercioflex.payment.infrastructure.storage;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.comercioflex.payment.application.PaymentReceiptObject;
import com.comercioflex.payment.application.PaymentReceiptStorage;
import com.comercioflex.payment.application.PaymentReceiptStorageException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.payments.receipt-storage.storage", havingValue = "s3")
public class S3PaymentReceiptStorage implements PaymentReceiptStorage {

	private static final String PRIVATE_PREFIX = "private/payment-receipts/";
	private final S3Client client;
	private final String bucket;

	public S3PaymentReceiptStorage(PaymentReceiptStorageProperties properties) {
		PaymentReceiptStorageProperties.S3 values = properties.getS3();
		if (!StringUtils.hasText(values.getBucket())) {
			throw new IllegalStateException(
				"PAYMENT_RECEIPT_S3_BUCKET is required when PAYMENT_RECEIPT_STORAGE=s3");
		}
		if (!StringUtils.hasText(values.getAccessKey())
				|| !StringUtils.hasText(values.getSecretKey())) {
			throw new IllegalStateException(
				"PAYMENT_RECEIPT_S3_ACCESS_KEY and PAYMENT_RECEIPT_S3_SECRET_KEY "
					+ "are required when PAYMENT_RECEIPT_STORAGE=s3");
		}
		if (!StringUtils.hasText(values.getRegion())) {
			throw new IllegalStateException(
				"PAYMENT_RECEIPT_S3_REGION is required when PAYMENT_RECEIPT_STORAGE=s3");
		}
		var builder = S3Client.builder()
			.region(Region.of(values.getRegion()))
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(values.isPathStyle()).build());
		if (StringUtils.hasText(values.getEndpoint())) {
			builder.endpointOverride(URI.create(values.getEndpoint()));
		}
		builder.credentialsProvider(StaticCredentialsProvider.create(
			AwsBasicCredentials.create(values.getAccessKey(), values.getSecretKey())));
		client = builder.build();
		bucket = values.getBucket();
	}

	@Override
	public void store(String key, byte[] bytes, String contentType) {
		try {
			client.putObject(PutObjectRequest.builder().bucket(bucket)
				.key(objectKey(key)).contentType(contentType).build(), RequestBody.fromBytes(bytes));
		}
		catch (RuntimeException exception) {
			throw failure("No se pudo guardar el comprobante privado.", exception);
		}
	}

	@Override
	public PaymentReceiptObject load(String key, String contentType) {
		try {
			return new PaymentReceiptObject(client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(bucket).key(objectKey(key)).build()).asByteArray(), contentType);
		}
		catch (RuntimeException exception) {
			throw failure("No se pudo leer el comprobante privado.", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucket)
				.key(objectKey(key)).build());
		}
		catch (RuntimeException exception) {
			throw failure("No se pudo eliminar el comprobante privado.", exception);
		}
	}

	String objectKey(String key) {
		if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/")) {
			throw failure("Clave de comprobante inválida.", null);
		}
		return PRIVATE_PREFIX + key;
	}

	String bucket() {
		return bucket;
	}

	private PaymentReceiptStorageException failure(String message, Throwable cause) {
		return new PaymentReceiptStorageException(message, cause);
	}
}
