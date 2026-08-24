package com.comercioflex.payment.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.comercioflex.payment.application.PaymentReceiptObject;
import com.comercioflex.payment.application.PaymentReceiptStorage;
import com.comercioflex.payment.application.PaymentReceiptStorageException;

@Component
@ConditionalOnProperty(
	name = "app.payments.receipt-storage.storage",
	havingValue = "local",
	matchIfMissing = true)
public class LocalPaymentReceiptStorage implements PaymentReceiptStorage {

	private final Path root;

	public LocalPaymentReceiptStorage(PaymentReceiptStorageProperties properties) {
		root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		}
		catch (IOException exception) {
			throw failure("No se pudo preparar el storage privado de comprobantes.", exception);
		}
	}

	@Override
	public void store(String key, byte[] bytes, String contentType) {
		Path target = resolve(key);
		Path temporary = null;
		try {
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(target.getParent(), ".receipt-", ".tmp");
			Files.write(temporary, bytes);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target);
			}
		}
		catch (IOException exception) {
			throw failure("No se pudo guardar el comprobante privado.", exception);
		}
		finally {
			if (temporary != null) {
				try { Files.deleteIfExists(temporary); }
				catch (IOException ignored) { }
			}
		}
	}

	@Override
	public PaymentReceiptObject load(String key, String contentType) {
		try {
			return new PaymentReceiptObject(Files.readAllBytes(resolve(key)), contentType);
		}
		catch (IOException exception) {
			throw failure("No se pudo leer el comprobante privado.", exception);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException exception) {
			throw failure("No se pudo eliminar el comprobante privado.", exception);
		}
	}

	private Path resolve(String key) {
		Path result = root.resolve(key).normalize();
		if (!result.startsWith(root)) {
			throw failure("Clave de comprobante inválida.", null);
		}
		return result;
	}

	private PaymentReceiptStorageException failure(String message, Throwable cause) {
		return new PaymentReceiptStorageException(message, cause);
	}
}
