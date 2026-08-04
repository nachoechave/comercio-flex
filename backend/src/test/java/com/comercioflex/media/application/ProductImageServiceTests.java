package com.comercioflex.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.media.domain.ProductImage;
import com.comercioflex.tenant.application.TenantContext;

class ProductImageServiceTests {

	private final ProductImageRepository repository = mock(ProductImageRepository.class);
	private final ProductImageStorage storage = mock(ProductImageStorage.class);
	private final ProductImageProcessor processor = mock(ProductImageProcessor.class);
	private final TransactionTemplate transactions = mock(TransactionTemplate.class);
	private final TenantContext tenantContext = new TenantContext();
	private ProductImageService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(transactions.execute(any())).thenAnswer(invocation ->
			((TransactionCallback<Object>) invocation.getArgument(0))
				.doInTransaction(mock(TransactionStatus.class)));
		service = new ProductImageService(repository, storage, processor, tenantContext, transactions);
	}

	@Test
	void rejectsArchivedProductAndCompensatesStoredObjects() {
		UUID productId = UUID.randomUUID();
		when(processor.process(any())).thenReturn(processed());
		when(repository.lockProduct(productId)).thenReturn(Optional.of(new LockedImageProduct(7, true)));

		try (TenantContext.Scope ignored = tenantContext.open("tenant-a")) {
			assertThatThrownBy(() -> service.replace(productId, new byte[] {1}, "Producto"))
				.isInstanceOf(ProductImageConflictException.class);
		}

		verify(repository, never()).upsert(eq(7L), any());
		verify(storage).delete(org.mockito.ArgumentMatchers.contains("/display.png"));
		verify(storage).delete(org.mockito.ArgumentMatchers.contains("/thumbnail.png"));
	}

	@Test
	void calculatesEtagFromRepresentationActuallyServed() {
		UUID imageId = UUID.randomUUID();
		ProductImage image = image(imageId);
		when(repository.findByPublicId(imageId, true)).thenReturn(Optional.of(image));
		when(storage.load(image.thumbnailStorageKey(), image.contentType()))
			.thenReturn(new StorageObject("thumbnail".getBytes(StandardCharsets.UTF_8), "image/png"));

		ProductImageService.ImageContent content = service.load(
			imageId, ProductImageService.ImageSize.THUMBNAIL, true);

		assertThat(content.etag()).isEqualTo(
			"80f61f96184524ba54db767ed49487392430ad26bf5cf2ef689905f3400325d7");
	}

	@Test
	void removesDisplayWhenThumbnailStorageFails() {
		UUID productId = UUID.randomUUID();
		when(processor.process(any())).thenReturn(processed());
		doNothing().doThrow(new ProductImageStorageException(
			"storage unavailable", new IllegalStateException("test")))
			.when(storage).store(any(), any(), any());

		try (TenantContext.Scope ignored = tenantContext.open("tenant-a")) {
			assertThatThrownBy(() -> service.replace(productId, new byte[] {1}, "Producto"))
				.isInstanceOf(ProductImageStorageException.class);
		}

		verify(storage).delete(org.mockito.ArgumentMatchers.contains("/display.png"));
		verify(repository, never()).upsert(anyLong(), any());
	}

	@Test
	void removesNewObjectsWhenDatabaseWriteFails() {
		UUID productId = UUID.randomUUID();
		when(processor.process(any())).thenReturn(processed());
		when(repository.lockProduct(productId)).thenReturn(Optional.empty());

		try (TenantContext.Scope ignored = tenantContext.open("tenant-a")) {
			assertThatThrownBy(() -> service.replace(productId, new byte[] {1}, "Producto"))
				.isInstanceOf(ProductImageNotFoundException.class);
		}

		verify(storage).delete(org.mockito.ArgumentMatchers.contains("/display.png"));
		verify(storage).delete(org.mockito.ArgumentMatchers.contains("/thumbnail.png"));
	}

	@Test
	void replacementDeletesPreviousObjectsAfterCommit() {
		UUID productId = UUID.randomUUID();
		ProductImage previous = image(UUID.randomUUID());
		ProductImage saved = image(UUID.randomUUID());
		when(processor.process(any())).thenReturn(processed());
		when(repository.lockProduct(productId)).thenReturn(Optional.of(new LockedImageProduct(7, false)));
		when(repository.upsert(eq(7L), any())).thenReturn(Optional.of(previous));
		when(repository.findByProductId(productId)).thenReturn(Optional.of(saved));

		try (TenantContext.Scope ignored = tenantContext.open("tenant-a")) {
			assertThat(service.replace(productId, new byte[] {1}, "Producto")).isEqualTo(saved);
		}

		verify(storage).delete(previous.displayStorageKey());
		verify(storage).delete(previous.thumbnailStorageKey());
	}

	private ProcessedProductImage processed() {
		return new ProcessedProductImage(new byte[] {1}, new byte[] {2},
			"image/png", "png", 10, 10, "a".repeat(64));
	}

	private ProductImage image(UUID id) {
		return new ProductImage(id, UUID.randomUUID(), "display", "thumbnail", "image/png",
			1, 1, 10, 10, "Producto", "a".repeat(64), 0, Instant.now());
	}
}
