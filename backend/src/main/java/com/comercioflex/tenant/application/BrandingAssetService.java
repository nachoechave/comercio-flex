package com.comercioflex.tenant.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.media.application.ProductImageNotFoundException;
import com.comercioflex.media.application.ProductImageProcessor;
import com.comercioflex.media.application.ProductImageStorage;
import com.comercioflex.tenant.domain.BrandAssetReference;
import com.comercioflex.tenant.domain.BrandAssetType;

@Service
public class BrandingAssetService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BrandingAssetService.class);

	private final TenantBrandingRepository repository;
	private final ProductImageStorage storage;
	private final ProductImageProcessor processor;
	private final TenantContext tenantContext;
	private final TransactionTemplate transactions;

	public BrandingAssetService(
			TenantBrandingRepository repository,
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

	public BrandAssetReference replace(BrandAssetType type, byte[] source) {
		var processed = processor.process(source);
		byte[] bytes = type == BrandAssetType.HERO
			? processed.displayBytes() : processed.thumbnailBytes();
		String etag = sha256(bytes);
		String tenantKey = tenantContext.currentDatabaseKey()
			.orElseThrow(() -> new IllegalStateException("Tenant context is required"));
		String key = tenantKey + "/branding/" + type.name().toLowerCase(Locale.ROOT)
			+ "/" + UUID.randomUUID() + "." + processed.extension();
		BrandAssetReference candidate = new BrandAssetReference(key, processed.contentType(), etag);
		storage.store(key, bytes, processed.contentType());
		BrandAssetReference previous;
		try {
			previous = transactions.execute(status -> repository.replaceAsset(type, candidate));
		}
		catch (RuntimeException exception) {
			deleteQuietly(candidate);
			throw exception;
		}
		deleteQuietly(previous);
		return candidate;
	}

	public void delete(BrandAssetType type) {
		BrandAssetReference previous = transactions.execute(status -> repository.clearAsset(type));
		deleteQuietly(previous);
	}

	public BrandAssetContent load(BrandAssetType type) {
		BrandAssetReference asset = transactions.execute(status -> repository.findCurrent()
			.map(branding -> switch (type) {
				case LOGO -> branding.logo();
				case FAVICON -> branding.favicon();
				case HERO -> branding.hero();
			})
			.orElse(null));
		if (asset == null) throw new ProductImageNotFoundException();
		var object = storage.load(asset.storageKey(), asset.contentType());
		return new BrandAssetContent(object.bytes(), object.contentType(), asset.etag());
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private void deleteQuietly(BrandAssetReference asset) {
		if (asset == null) return;
		try {
			storage.delete(asset.storageKey());
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Could not remove orphaned branding object {}", asset.storageKey(), exception);
		}
	}

	public record BrandAssetContent(byte[] bytes, String contentType, String etag) {
	}
}
