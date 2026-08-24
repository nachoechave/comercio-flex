package com.comercioflex.payment.api;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.comercioflex.payment.application.BankTransferPaymentException;
import com.comercioflex.payment.application.PaymentReceiptStorageException;

@RestControllerAdvice(assignableTypes = {
	PublicBankTransferController.class, AdminBankTransferController.class
})
public class BankTransferPaymentErrorHandler {

	@ExceptionHandler(BankTransferPaymentException.class)
	ProblemDetail payment(BankTransferPaymentException exception) {
		HttpStatus status = switch (exception.code()) {
			case "BANK_TRANSFER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "INVALID_BANK_TRANSFER_RECEIPT",
				 "BANK_TRANSFER_REJECTION_REASON_REQUIRED",
				 "BANK_TRANSFER_REJECTION_REASON_INVALID" -> HttpStatus.BAD_REQUEST;
			default -> HttpStatus.CONFLICT;
		};
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
		problem.setTitle("No se pudo procesar la transferencia");
		problem.setType(URI.create("https://comercio-flex/errors/bank-transfer"));
		problem.setProperty("code", exception.code());
		return problem;
	}

	@ExceptionHandler(PaymentReceiptStorageException.class)
	ProblemDetail storage(PaymentReceiptStorageException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
			HttpStatus.SERVICE_UNAVAILABLE, "No se pudo acceder al comprobante.");
		problem.setTitle("Storage de comprobantes no disponible");
		problem.setType(URI.create("https://comercio-flex/errors/payment-receipt-storage"));
		problem.setProperty("code", "PAYMENT_RECEIPT_STORAGE_UNAVAILABLE");
		return problem;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail tooLarge(MaxUploadSizeExceededException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
			HttpStatus.BAD_REQUEST, "El comprobante supera el máximo de 5 MB.");
		problem.setTitle("Comprobante inválido");
		problem.setType(URI.create("https://comercio-flex/errors/bank-transfer"));
		problem.setProperty("code", "INVALID_BANK_TRANSFER_RECEIPT");
		return problem;
	}
}
