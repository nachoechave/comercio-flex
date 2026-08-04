package com.comercioflex.media.infrastructure.storage;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.comercioflex.media.application.ProductImageStorage;
import com.comercioflex.media.application.ProductImageStorageException;
import com.comercioflex.media.application.StorageObject;
import com.comercioflex.media.config.ProductMediaProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.media.storage", havingValue = "s3")
public class S3ProductImageStorage implements ProductImageStorage {

	private final S3Client client;
	private final String bucket;

	public S3ProductImageStorage(ProductMediaProperties properties) {
		ProductMediaProperties.S3 values = properties.getS3();
		if (!StringUtils.hasText(values.getBucket())) {
			throw new IllegalStateException("MEDIA_S3_BUCKET is required for S3 storage");
		}
		var builder = S3Client.builder()
			.region(Region.of(values.getRegion()))
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(values.isPathStyle())
				.build());
		if (StringUtils.hasText(values.getEndpoint())) {
			builder.endpointOverride(URI.create(values.getEndpoint()));
		}
		if (StringUtils.hasText(values.getAccessKey()) || StringUtils.hasText(values.getSecretKey())) {
			if (!StringUtils.hasText(values.getAccessKey()) || !StringUtils.hasText(values.getSecretKey())) {
				throw new IllegalStateException("Both S3 access key and secret key are required");
			}
			builder.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create(values.getAccessKey(), values.getSecretKey())));
		}
		client = builder.build();
		bucket = values.getBucket();
	}

	@Override
	public void store(String key, byte[] bytes, String contentType) {
		try {
			client.putObject(PutObjectRequest.builder()
				.bucket(bucket).key(key).contentType(contentType).build(),
				RequestBody.fromBytes(bytes));
		}
		catch (RuntimeException exception) {
			throw failure("No se pudo guardar la imagen.", exception);
		}
	}

	@Override
	public StorageObject load(String key, String contentType) {
		try {
			byte[] bytes = client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(bucket).key(key).build()).asByteArray();
			return new StorageObject(bytes, contentType);
		}
		catch (RuntimeException exception) {
			throw failure("No se pudo leer la imagen.", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		}
		catch (RuntimeException exception) {
			throw failure("No se pudo eliminar la imagen.", exception);
		}
	}

	private ProductImageStorageException failure(String message, RuntimeException cause) {
		return new ProductImageStorageException(message, cause);
	}
}
