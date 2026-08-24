package com.comercioflex.payment.api;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.comercioflex.payment.application.BankTransferPaymentException;
import com.comercioflex.payment.application.BankTransferPaymentService;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/orders/{orderId}/payments/bank-transfer")
public class PublicBankTransferController {

	private final BankTransferPaymentService service;

	public PublicBankTransferController(BankTransferPaymentService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<BankTransferPaymentResponse> initiate(
			@PathVariable String storeSlug,
			@PathVariable UUID orderId,
			@RequestParam String token) {
		return noStore(BankTransferPaymentResponse.from(
			service.initiate(storeSlug, orderId, token)));
	}

	@GetMapping
	ResponseEntity<BankTransferPaymentResponse> findCurrent(
			@PathVariable UUID orderId,
			@RequestParam String token) {
		return noStore(BankTransferPaymentResponse.from(service.findCurrent(orderId, token)));
	}

	@GetMapping("/{paymentId}")
	ResponseEntity<BankTransferPaymentResponse> find(
			@PathVariable UUID orderId,
			@PathVariable UUID paymentId,
			@RequestParam String token) {
		return noStore(BankTransferPaymentResponse.from(
			service.find(orderId, token, paymentId)));
	}

	@PostMapping(path = "/{paymentId}/receipt", consumes = "multipart/form-data")
	ResponseEntity<BankTransferPaymentResponse> upload(
			@PathVariable String storeSlug,
			@PathVariable UUID orderId,
			@PathVariable UUID paymentId,
			@RequestParam String token,
			@RequestPart("file") MultipartFile file) {
		if (file.getSize() > BankTransferPaymentService.MAX_RECEIPT_SIZE) {
			throw new BankTransferPaymentException(
				"INVALID_BANK_TRANSFER_RECEIPT", "El comprobante supera el máximo de 5 MB.");
		}
		try {
			return noStore(BankTransferPaymentResponse.from(service.upload(
				storeSlug, orderId, token, paymentId, file.getOriginalFilename(),
				file.getContentType(), file.getBytes())));
		}
		catch (IOException exception) {
			throw new BankTransferPaymentException(
				"INVALID_BANK_TRANSFER_RECEIPT", "No se pudo leer el comprobante.");
		}
	}

	private ResponseEntity<BankTransferPaymentResponse> noStore(
			BankTransferPaymentResponse body) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}
}
