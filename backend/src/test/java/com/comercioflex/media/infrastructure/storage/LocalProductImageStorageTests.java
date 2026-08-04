package com.comercioflex.media.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.comercioflex.media.application.ProductImageStorageException;
import com.comercioflex.media.config.ProductMediaProperties;

class LocalProductImageStorageTests {

	@TempDir Path temporaryDirectory;

	@Test
	void storesReplacesLoadsAndDeletesAtomically() throws Exception {
		LocalProductImageStorage storage = storage();
		storage.store("tenant/products/image/display.png", new byte[] {1}, "image/png");
		storage.store("tenant/products/image/display.png", new byte[] {2, 3}, "image/png");

		assertThat(storage.load("tenant/products/image/display.png", "image/png").bytes())
			.containsExactly(2, 3);
		assertThat(Files.walk(temporaryDirectory)
			.filter(path -> path.getFileName().toString().startsWith(".upload-")))
			.isEmpty();

		storage.delete("tenant/products/image/display.png");
		assertThat(Files.exists(temporaryDirectory.resolve("tenant/products/image/display.png")))
			.isFalse();
	}

	@Test
	void refusesPathTraversal() {
		assertThatThrownBy(() -> storage().store("../../outside", new byte[] {1}, "image/png"))
			.isInstanceOf(ProductImageStorageException.class);
	}

	private LocalProductImageStorage storage() {
		ProductMediaProperties properties = new ProductMediaProperties();
		properties.setLocalRoot(temporaryDirectory.toString());
		return new LocalProductImageStorage(properties);
	}
}
