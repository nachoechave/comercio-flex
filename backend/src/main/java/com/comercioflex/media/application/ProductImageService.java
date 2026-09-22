package com.comercioflex.media.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

	public List<ProductImage> add(UUID productId, List<byte[]> sources, String rawAltText) {
		if (sources == null || sources.isEmpty() || sources.size() > 6) throw limit();
		String altText = normalizeAltText(rawAltText);
		List<String> storedKeys = new ArrayList<>();
		try {
			return transactions.execute(status -> {
				LockedImageProduct product = repository.lockProduct(productId)
					.orElseThrow(ProductImageNotFoundException::new);
				requireEditable(product);
				var existing = repository.findAll(productId);
				if (existing.size() + sources.size() > 6) throw limit();
				if (!existing.isEmpty() && existing.stream().noneMatch(ProductImage::primaryImage)) {
					repository.arrange(productId, existing.stream().map(ProductImage::id).toList(), existing.getFirst().id());
				}
				String tenantKey = tenantContext.currentDatabaseKey().orElseThrow();
				int position = existing.size();
				for (byte[] source : sources) {
					var processed = processor.process(source);
					UUID id = UUID.randomUUID();
					String prefix = tenantKey + "/products/" + productId + "/" + id;
					String display = prefix + "/display." + processed.extension();
					String thumbnail = prefix + "/thumbnail." + processed.extension();
					storedKeys.add(display);
					storage.store(display, processed.displayBytes(), processed.contentType());
					storedKeys.add(thumbnail);
					storage.store(thumbnail, processed.thumbnailBytes(), processed.contentType());
					var image = new ProductImage(id, productId, display, thumbnail, processed.contentType(),
						processed.displayBytes().length, processed.thumbnailBytes().length,
						processed.width(), processed.height(), altText, processed.sha256(), 0, Instant.EPOCH);
					repository.insert(product.internalId(), image, position, position == 0);
					position++;
				}
				return repository.findAll(productId);
			});
		}
		catch (RuntimeException exception) {
			storedKeys.forEach(this::deleteQuietly);
			throw exception;
		}
	}

	public List<ProductImage> delete(UUID productId, UUID imageId) {
		ProductImage[] removed = new ProductImage[1];
		var result = transactions.execute(status -> {
			var images = editableImages(productId);
			removed[0] = images.stream().filter(image -> image.id().equals(imageId))
				.findFirst().orElseThrow(ProductImageNotFoundException::new);
			repository.deleteImage(productId, imageId);
			var remaining = images.stream().filter(image -> !image.id().equals(imageId)).toList();
			UUID primary = remaining.isEmpty() ? null : remaining.stream().filter(ProductImage::primaryImage)
				.findFirst().orElse(remaining.getFirst()).id();
			repository.arrange(productId, remaining.stream().map(ProductImage::id).toList(), primary);
			return repository.findAll(productId);
		});
		deleteObjectsQuietly(removed[0]);
		return result;
	}

	public List<ProductImage> primary(UUID productId, UUID imageId) {
		return transactions.execute(status -> {
			var images = editableImages(productId);
			if (images.stream().noneMatch(image -> image.id().equals(imageId))) throw new ProductImageNotFoundException();
			repository.arrange(productId, images.stream().map(ProductImage::id).toList(), imageId);
			return repository.findAll(productId);
		});
	}

	public List<ProductImage> reorder(UUID productId, List<UUID> ids) {
		return transactions.execute(status -> {
			var images = editableImages(productId);
			if (ids == null || ids.size() != images.size() || ids.stream().anyMatch(java.util.Objects::isNull)
					|| !new HashSet<>(ids).equals(new HashSet<>(images.stream().map(ProductImage::id).toList()))) {
				throw new InvalidProductImageException("El orden debe incluir todas las imágenes del producto, sin repetirlas.");
			}
			UUID primary = images.isEmpty() ? null : images.stream().filter(ProductImage::primaryImage)
				.findFirst().orElse(images.getFirst()).id();
			repository.arrange(productId, ids, primary);
			return repository.findAll(productId);
		});
	}

	private List<ProductImage> editableImages(UUID productId) {
		var product = repository.lockProduct(productId).orElseThrow(ProductImageNotFoundException::new);
		requireEditable(product);
		return repository.findAll(productId);
	}

	private InvalidProductImageException limit() {
		return new InvalidProductImageException("El producto puede tener como máximo 6 imágenes.");
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
