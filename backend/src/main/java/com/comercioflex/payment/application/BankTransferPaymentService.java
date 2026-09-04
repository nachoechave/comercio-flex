package com.comercioflex.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.payment.domain.BankTransferStatus;

@Service
public class BankTransferPaymentService {

	public static final long MAX_RECEIPT_SIZE = 5L * 1024 * 1024;
	private static final Duration BANK_RESERVATION_DURATION = Duration.ofHours(24);
	private static final String TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$";

	private final BankTransferRepository repository;
	private final PaymentReceiptStorage storage;
	private final PaidOrderConfirmer orderConfirmer;
	private final CustomerNotificationPublisher notifications;
	private final TransactionTemplate transactions;
	private final Clock clock;

	@Autowired
	public BankTransferPaymentService(
			BankTransferRepository repository,
			PaymentReceiptStorage storage,
			PaidOrderConfirmer orderConfirmer,
			CustomerNotificationPublisher notifications,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactions) {
		this(repository, storage, orderConfirmer, notifications, transactions, Clock.systemUTC());
	}

	BankTransferPaymentService(
			BankTransferRepository repository,
			PaymentReceiptStorage storage,
			PaidOrderConfirmer orderConfirmer,
			TransactionTemplate transactions,
			Clock clock) {
		this(repository, storage, orderConfirmer, CustomerNotificationPublisher.noop(),
			transactions, clock);
	}

	BankTransferPaymentService(
			BankTransferRepository repository,
			PaymentReceiptStorage storage,
			PaidOrderConfirmer orderConfirmer,
			CustomerNotificationPublisher notifications,
			TransactionTemplate transactions,
			Clock clock) {
		this.repository = repository;
		this.storage = storage;
		this.orderConfirmer = orderConfirmer;
		this.notifications = notifications;
		this.transactions = transactions;
		this.clock = clock;
	}

	public BankTransferInstructions initiate(
			String tenantSlug, UUID orderId, String lookupToken) {
		validatePublicRequest(orderId, lookupToken);
		return Objects.requireNonNull(transactions.execute(status -> {
			BankTransferConfiguration configuration = requireEnabled();
			BankTransferOrder order = repository.lockOrder(orderId, sha256(lookupToken))
				.orElseThrow(this::notFound);

			if (order.paymentMethod() != OrderPaymentMethod.BANK_TRANSFER) {
					throw conflict(
							"BANK_TRANSFER_PAYMENT_METHOD_MISMATCH",
							"El pedido fue creado para Mercado Pago.");
			}

			var current = repository.findCurrentForOrder(order.internalId());
			if (current.isPresent()
					&& current.get().status() != BankTransferStatus.REJECTED) {
				return instructions(current.get(), configuration);
			}
			Instant now = clock.instant();
			if (order.status() != OrderStatus.PENDING_CONFIRMATION
					|| !order.reservationExpiresAt().isAfter(now)) {
				throw conflict("BANK_TRANSFER_ORDER_NOT_PAYABLE",
					"El pedido o su reserva ya no están disponibles.");
			}
			if (repository.hasBlockingCheckout(order.internalId())) {
				throw conflict("PAYMENT_ALREADY_IN_PROGRESS",
					"El pedido ya tiene otro pago en proceso.");
			}
			Instant expiresAt = now.plus(BANK_RESERVATION_DURATION);
			repository.extendReservation(order.internalId(), expiresAt);
			UUID paymentId = UUID.randomUUID();
			repository.insert(paymentId, order.internalId(),
				repository.nextAttemptNumber(order.internalId()));
			return instructions(repository.findById(paymentId, false)
				.orElseThrow(this::notFound), configuration);
		}));
	}

	public BankTransferInstructions find(
			UUID orderId, String lookupToken, UUID paymentId) {
		validatePublicRequest(orderId, lookupToken);
		return Objects.requireNonNull(transactions.execute(status -> {
			BankTransferConfiguration configuration = repository.findConfiguration();
			BankTransferPayment payment = repository.findByIdAndOrderToken(
				paymentId, orderId, sha256(lookupToken), false).orElseThrow(this::notFound);
			return instructions(payment, configuration);
		}));
	}

	public BankTransferInstructions findCurrent(UUID orderId, String lookupToken) {
		validatePublicRequest(orderId, lookupToken);
		return Objects.requireNonNull(transactions.execute(status -> {
			BankTransferConfiguration configuration = repository.findConfiguration();
			BankTransferOrder order = repository.lockOrder(orderId, sha256(lookupToken))
				.orElseThrow(this::notFound);
			BankTransferPayment payment = repository.findCurrentForOrder(order.internalId())
				.orElseThrow(this::notFound);
			return instructions(payment, configuration);
		}));
	}

	public BankTransferInstructions upload(
			String tenantSlug, UUID orderId, String lookupToken, UUID paymentId,
			String originalFilename, String declaredContentType, byte[] bytes) {
		validatePublicRequest(orderId, lookupToken);
		ValidatedReceipt receipt = validateReceipt(originalFilename, declaredContentType, bytes);
		byte[] tokenHash = sha256(lookupToken);
		Objects.requireNonNull(transactions.execute(status -> {
			BankTransferPayment payment = repository.findByIdAndOrderToken(
				paymentId, orderId, tokenHash, true).orElseThrow(this::notFound);
			requireUploadable(payment);
			return payment;
		}));
		String objectKey = objectKey(tenantSlug, orderId);
		storage.store(objectKey, bytes, receipt.contentType());
		try {
			return Objects.requireNonNull(transactions.execute(status -> {
				BankTransferConfiguration configuration = repository.findConfiguration();
				BankTransferPayment locked = repository.findByIdAndOrderToken(
					paymentId, orderId, tokenHash, true).orElseThrow(this::notFound);
				requireUploadable(locked);
				repository.attachReceipt(locked, objectKey, receipt.filename(),
					receipt.contentType(), bytes.length, clock.instant());
				return instructions(repository.findById(paymentId, false)
					.orElseThrow(this::notFound), configuration);
			}));
		}
		catch (RuntimeException exception) {
			try { storage.delete(objectKey); }
			catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
			throw exception;
		}
	}

	public List<BankTransferPayment> findPendingReview() {
		return Objects.requireNonNull(transactions.execute(status ->
			repository.findPendingReview(100)));
	}

	public BankTransferPayment findAdmin(UUID paymentId) {
		return Objects.requireNonNull(transactions.execute(status ->
			repository.findById(paymentId, false).orElseThrow(this::notFound)));
	}

	public DownloadedPaymentReceipt download(UUID paymentId) {
		BankTransferPayment payment = findAdmin(paymentId);
		if (payment.receiptObjectKey() == null || payment.receiptContentType() == null) {
			throw notFound();
		}
		return new DownloadedPaymentReceipt(
			storage.load(payment.receiptObjectKey(), payment.receiptContentType()),
			payment.receiptOriginalFilename());
	}

	public BankTransferPayment approve(UUID paymentId, long reviewerId) {
		ApprovalResult result = Objects.requireNonNull(transactions.execute(status -> {
			BankTransferPayment payment = repository.findById(paymentId, true)
				.orElseThrow(this::notFound);
			if (payment.status() == BankTransferStatus.APPROVED) {
				return new ApprovalResult(payment, false);
			}
			if (payment.status() != BankTransferStatus.UNDER_REVIEW) {
				throw conflict("BANK_TRANSFER_NOT_REVIEWABLE",
					"El comprobante no está pendiente de revisión.");
			}
			OrderTransitionExecution confirmation = orderConfirmer
				.confirmWithinCurrentTransaction(
					payment.orderId(), approvalKey(payment.id()), "Transferencia bancaria");
			if (confirmation.expired()) {
				return new ApprovalResult(payment, true);
			}
			repository.approve(payment, reviewerId, clock.instant());
			return new ApprovalResult(repository.findById(paymentId, false)
				.orElseThrow(this::notFound), false);
		}));
		if (result.expired()) {
			throw conflict("BANK_TRANSFER_RESERVATION_EXPIRED",
				"La reserva venció y el pago no puede aprobarse.");
		}
		return result.payment();
	}

	public BankTransferPayment reject(UUID paymentId, long reviewerId, String rawReason) {
		String reason = normalizeReason(rawReason);
		return Objects.requireNonNull(transactions.execute(status -> {
			BankTransferPayment payment = repository.findById(paymentId, true)
				.orElseThrow(this::notFound);
			if (payment.status() == BankTransferStatus.REJECTED) {
				return payment;
			}
			if (payment.status() != BankTransferStatus.UNDER_REVIEW) {
				throw conflict("BANK_TRANSFER_NOT_REVIEWABLE",
					"El comprobante no está pendiente de revisión.");
			}
			repository.reject(payment, reviewerId, reason, clock.instant());
			BankTransferPayment rejected = repository.findById(paymentId, false)
				.orElseThrow(this::notFound);
			notifications.bankTransferReceiptRejected(rejected, clock.instant());
			return rejected;
		}));
	}

	private BankTransferConfiguration requireEnabled() {
		BankTransferConfiguration configuration = repository.findConfiguration();
		if (!configuration.enabled()) {
			throw conflict("BANK_TRANSFER_DISABLED",
				"La transferencia bancaria no está habilitada para esta tienda.");
		}
		return configuration;
	}

	private BankTransferInstructions instructions(
			BankTransferPayment payment, BankTransferConfiguration configuration) {
		Instant now = clock.instant();
		return new BankTransferInstructions(
			payment, configuration.bankName(), configuration.accountHolder(),
			configuration.alias(), configuration.cbuCvu(),
			(payment.status() == BankTransferStatus.AWAITING_RECEIPT
				|| payment.status() == BankTransferStatus.REJECTED)
				&& payment.reservationExpiresAt().isAfter(now), now);
	}

	private void requireUploadable(BankTransferPayment payment) {
		if (!payment.canUpload(clock.instant())) {
			throw conflict("BANK_TRANSFER_RECEIPT_NOT_UPLOADABLE",
				"El comprobante no puede cargarse para este intento.");
		}
	}

	private ValidatedReceipt validateReceipt(
			String originalFilename, String declaredContentType, byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw invalidFile("El comprobante está vacío.");
		}
		if (bytes.length > MAX_RECEIPT_SIZE) {
			throw invalidFile("El comprobante supera el máximo de 5 MB.");
		}
		String detected = detectContentType(bytes);
		String declared = declaredContentType == null ? "" : declaredContentType
			.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		if (detected == null || !detected.equals(declared)) {
			throw invalidFile("El archivo debe ser JPEG, PNG o PDF válido.");
		}
		return new ValidatedReceipt(safeFilename(originalFilename, detected), detected);
	}

	private String detectContentType(byte[] bytes) {
		if (bytes.length >= 4 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
				&& (bytes[2] & 0xff) == 0xff
				&& (bytes[bytes.length - 2] & 0xff) == 0xff
				&& (bytes[bytes.length - 1] & 0xff) == 0xd9) {
			return "image/jpeg";
		}
		byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
		if (bytes.length >= png.length
				&& Arrays.equals(Arrays.copyOf(bytes, png.length), png)) {
			return "image/png";
		}
		byte[] pdf = "%PDF-".getBytes(StandardCharsets.US_ASCII);
		if (bytes.length >= pdf.length
				&& Arrays.equals(Arrays.copyOf(bytes, pdf.length), pdf)
				&& new String(bytes, Math.max(0, bytes.length - Math.min(bytes.length, 1024)),
					Math.min(bytes.length, 1024), StandardCharsets.ISO_8859_1).contains("%%EOF")) {
			return "application/pdf";
		}
		return null;
	}

	private String safeFilename(String value, String contentType) {
		String fallback = switch (contentType) {
			case "image/jpeg" -> "comprobante.jpg";
			case "image/png" -> "comprobante.png";
			default -> "comprobante.pdf";
		};
		if (value == null || value.isBlank()) return fallback;
		String cleaned = value.replace('\\', '/');
		cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1)
			.replaceAll("[\\p{Cntrl}]", "").trim();
		if (cleaned.isBlank()) return fallback;
		return cleaned.length() <= 255 ? cleaned : cleaned.substring(cleaned.length() - 255);
	}

	private String objectKey(String tenantSlug, UUID orderId) {
		String safeTenant = tenantSlug == null ? "tenant"
			: tenantSlug.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
		return "bank-transfer-receipts/%s/%s/%s".formatted(
			safeTenant, orderId, UUID.randomUUID());
	}

	private UUID approvalKey(UUID paymentId) {
		return UUID.nameUUIDFromBytes(
			("bank-transfer-approval:" + paymentId).getBytes(StandardCharsets.UTF_8));
	}

	private String normalizeReason(String value) {
		if (value == null || value.isBlank()) {
			throw conflict("BANK_TRANSFER_REJECTION_REASON_REQUIRED",
				"Indicá el motivo del rechazo.");
		}
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > 500 || normalized.chars().anyMatch(Character::isISOControl)) {
			throw conflict("BANK_TRANSFER_REJECTION_REASON_INVALID",
				"El motivo del rechazo no es válido.");
		}
		return normalized;
	}

	private void validatePublicRequest(UUID orderId, String lookupToken) {
		if (orderId == null || lookupToken == null || !lookupToken.matches(TOKEN_PATTERN)) {
			throw notFound();
		}
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	private BankTransferPaymentException invalidFile(String message) {
		return new BankTransferPaymentException("INVALID_BANK_TRANSFER_RECEIPT", message);
	}

	private BankTransferPaymentException notFound() {
		return new BankTransferPaymentException(
			"BANK_TRANSFER_NOT_FOUND", "La transferencia no existe.");
	}

	private BankTransferPaymentException conflict(String code, String message) {
		return new BankTransferPaymentException(code, message);
	}

	private record ValidatedReceipt(String filename, String contentType) { }
	private record ApprovalResult(BankTransferPayment payment, boolean expired) { }
}
