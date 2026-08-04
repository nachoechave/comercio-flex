package com.comercioflex.media.application;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.media.domain.ProductImage;
import com.comercioflex.tenant.application.TenantContext;

@Service
public class ProductImageService {

	private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

	private final ProductImageRepository repository;
	private final ProductImageStorage storage;
	private final ProductImageProcessor processor;
	private final TenantContext tenantContext;
	private final TransactionTemplate transactions;

	public ProductImageService(
			ProductImageRepository repository,
			ProductImageStorage storage,
			ProductImageProcessor processor,
			TenantContext tenantContext,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactions) {
		this.repository = repository;
		this.storage = storage;
		this.processor = processor;
		this.tenantContext = tenantContext;
		this.transactions = transactions;
	}

	public ProductImage replace(UUID productId, byte[] source, String rawAltText) {
		String altText = normalizeAltText(rawAltText);
		ProcessedProductImage processed = processor.process(source);
		UUID imageId = UUID.randomUUID();
		String tenantKey = tenantContext.currentDatabaseKey()
			.orElseThrow(() -> new IllegalStateException("Tenant context is required"));
		String prefix = tenantKey + "/products/" + productId + "/" + imageId;
		String displayKey = prefix + "/display." + processed.extension();
		String thumbnailKey = prefix + "/thumbnail." + processed.extension();

		storage.store(displayKey, processed.displayBytes(), processed.contentType());
		try {
			storage.store(thumbnailKey, processed.thumbnailBytes(), processed.contentType());
		}
		catch (RuntimeException exception) {
			deleteQuietly(displayKey);
			throw exception;
		}

		ProductImage candidate = new ProductImage(
			imageId, productId, displayKey, thumbnailKey, processed.contentType(),
			processed.displayBytes().length, processed.thumbnailBytes().length,
			processed.width(), processed.height(), altText, processed.sha256(),
			0, Instant.EPOCH);
		ProductImage previous;
		try {
			previous = transactions.execute(status -> {
				LockedImageProduct product = repository.lockProduct(productId)
					.orElseThrow(ProductImageNotFoundException::new);
				requireEditable(product);
				return repository.upsert(product.internalId(), candidate).orElse(null);
			});
		}
		catch (RuntimeException exception) {
			deleteQuietly(displayKey);
			deleteQuietly(thumbnailKey);
			throw exception;
		}
		if (previous != null) deleteObjectsQuietly(previous);
		return transactions.execute(status -> repository.findByProductId(productId)
			.orElseThrow(ProductImageNotFoundException::new));
	}

	public void delete(UUID productId) {
		ProductImage removed = transactions.execute(status -> {
			LockedImageProduct product = repository.lockProduct(productId)
				.orElseThrow(ProductImageNotFoundException::new);
			requireEditable(product);
			return repository.delete(productId).orElseThrow(ProductImageNotFoundException::new);
		});
		deleteObjectsQuietly(removed);
	}

	public ImageContent load(UUID imageId, ImageSize size, boolean requirePublished) {
		ProductImage image = transactions.execute(status -> repository
			.findByPublicId(imageId, requirePublished)
			.orElseThrow(ProductImageNotFoundException::new));
		String key = size == ImageSize.THUMBNAIL
			? image.thumbnailStorageKey() : image.displayStorageKey();
		StorageObject object = storage.load(key, image.contentType());
		return new ImageContent(object.bytes(), object.contentType(), sha256(object.bytes()));
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String normalizeAltText(String value) {
		String normalized = value == null ? "" : value.strip().replaceAll("\\s+", " ");
		if (normalized.isEmpty() || normalized.length() > 180) {
			throw new InvalidProductImageException(
				"El texto alternativo es obligatorio y admite hasta 180 caracteres.");
		}
		return normalized;
	}

	private void requireEditable(LockedImageProduct product) {
		if (product.archived()) {
			throw new ProductImageConflictException(
				"No se puede modificar la imagen de un producto archivado.");
		}
	}

	private void deleteObjectsQuietly(ProductImage image) {
		deleteQuietly(image.displayStorageKey());
		deleteQuietly(image.thumbnailStorageKey());
	}

	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (RuntimeException exception) {
			log.warn("Could not remove orphaned product media object {}", key, exception);
		}
	}

	public enum ImageSize {
		DISPLAY, THUMBNAIL;

		public static ImageSize parse(String value) {
			try {
				return valueOf(value.toUpperCase(Locale.ROOT));
			}
			catch (RuntimeException exception) {
				throw new ProductImageNotFoundException();
			}
		}
	}

	public record ImageContent(byte[] bytes, String contentType, String etag) {
	}
}
