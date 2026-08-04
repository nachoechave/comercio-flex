package com.comercioflex.media.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.comercioflex.media.application.ProductImageStorage;
import com.comercioflex.media.application.ProductImageStorageException;
import com.comercioflex.media.application.StorageObject;
import com.comercioflex.media.config.ProductMediaProperties;

@Component
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalProductImageStorage implements ProductImageStorage {

	private final Path root;

	public LocalProductImageStorage(ProductMediaProperties properties) {
		root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		}
		catch (IOException exception) {
			throw failure("No se pudo preparar el almacenamiento local.", exception);
		}
	}

	@Override
	public void store(String key, byte[] bytes, String contentType) {
		Path target = resolve(key);
		Path temporary = null;
		try {
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			Files.write(temporary, bytes);
			try {
				Files.move(temporary, target,
					StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException exception) {
			throw failure("No se pudo guardar la imagen.", exception);
		}
		finally {
			if (temporary != null) {
				try { Files.deleteIfExists(temporary); }
				catch (IOException ignored) { }
			}
		}
	}

	@Override
	public StorageObject load(String key, String contentType) {
		try {
			return new StorageObject(Files.readAllBytes(resolve(key)), contentType);
		}
		catch (IOException exception) {
			throw failure("No se pudo leer la imagen.", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException exception) {
			throw failure("No se pudo eliminar la imagen.", exception);
		}
	}

	private Path resolve(String key) {
		Path result = root.resolve(key).normalize();
		if (!result.startsWith(root)) {
			throw new ProductImageStorageException("Clave de almacenamiento inválida.", null);
		}
		return result;
	}

	private ProductImageStorageException failure(String message, Throwable cause) {
		return new ProductImageStorageException(message, cause);
	}
}
