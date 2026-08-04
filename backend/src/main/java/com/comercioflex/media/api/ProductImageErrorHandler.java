package com.comercioflex.media.api;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.comercioflex.media.application.InvalidProductImageException;
import com.comercioflex.media.application.ProductImageNotFoundException;
import com.comercioflex.media.application.ProductImageConflictException;
import com.comercioflex.media.application.ProductImageStorageException;

@RestControllerAdvice(basePackageClasses = AdminProductImageController.class)
public class ProductImageErrorHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageErrorHandler.class);

	@ExceptionHandler(ProductImageNotFoundException.class)
	ProblemDetail notFound() {
		return problem(HttpStatus.NOT_FOUND, "Imagen no encontrada",
			"No existe la imagen solicitada.", "product-image-not-found");
	}

	@ExceptionHandler({InvalidProductImageException.class, MaxUploadSizeExceededException.class})
	ProblemDetail invalid(RuntimeException exception) {
		String detail = exception instanceof InvalidProductImageException
			? exception.getMessage() : "La imagen no puede superar 5 MB.";
		return problem(HttpStatus.BAD_REQUEST, "Imagen inválida", detail, "invalid-product-image");
	}

	@ExceptionHandler(ProductImageStorageException.class)
	ProblemDetail unavailable(ProductImageStorageException exception) {
		LOGGER.error("Product image storage is unavailable", exception);
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Imágenes no disponibles",
			"No pudimos acceder al almacenamiento de imágenes.", "product-image-storage-unavailable");
	}

	@ExceptionHandler(ProductImageConflictException.class)
	ProblemDetail conflict(ProductImageConflictException exception) {
		return problem(HttpStatus.CONFLICT, "Imagen no modificable",
			exception.getMessage(), "product-image-conflict");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("https://comercio-flex.local/problems/" + type));
		return problem;
	}
}
