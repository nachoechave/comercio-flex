package com.comercioflex.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentResultStatus;

public class PaymentApplicationService {

	private final PaymentRepository repository;
	private final PaymentGateway gateway;
	private final PaidOrderConfirmer orderConfirmer;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public PaymentApplicationService(
			PaymentRepository repository,
			PaymentGateway gateway,
			PaidOrderConfirmer orderConfirmer,
			TransactionTemplate transactionTemplate) {
		this(
			repository,
			gateway,
			orderConfirmer,
			transactionTemplate,
			Clock.systemUTC());
	}

	PaymentApplicationService(
			PaymentRepository repository,
			PaymentGateway gateway,
			PaidOrderConfirmer orderConfirmer,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.repository = repository;
		this.gateway = gateway;
		this.orderConfirmer = orderConfirmer;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	public PaymentInitiation initiate(PaymentCommand rawCommand) {
		PaymentCommand command = validate(rawCommand);
		CreatedOrReplayed created = createOrResolveConcurrentReplay(command);
		if (created == null) {
			throw new IllegalStateException("No se pudo iniciar el pago.");
		}
		if (created.replayed()) {
			return new PaymentInitiation(created.intent().toDomain(), true);
		}

		GatewayPayment gatewayPayment;
		try {
			gatewayPayment = gateway.createPayment(new GatewayPaymentRequest(
				created.intent().id(),
				created.intent().externalReference(),
				created.intent().amount(),
				created.intent().currencyCode()));
		}
		catch (RuntimeException exception) {
			markForReviewPreserving(created.intent().id(), exception);
			throw exception;
		}
		StoredPaymentIntent applied;
		try {
			applied = transactionTemplate.execute(
				ignored -> applyGatewayPayment(created.intent().id(), gatewayPayment));
		}
		catch (RuntimeException exception) {
			markForReviewPreserving(created.intent().id(), exception);
			if (exception instanceof DataIntegrityViolationException
					|| exception instanceof TransientDataAccessException) {
				throw new PaymentConflictException(
					"El identificador del proveedor pertenece a otra operación.");
			}
			throw exception;
		}
		if (applied == null) {
			throw new IllegalStateException("No se pudo registrar el resultado del pago.");
		}
		return new PaymentInitiation(applied.toDomain(), created.replayed());
	}

	private CreatedOrReplayed createOrResolveConcurrentReplay(
			PaymentCommand command) {
		try {
			return transactionTemplate.execute(ignored -> createOrReplay(command));
		}
		catch (DataIntegrityViolationException | TransientDataAccessException exception) {
			CreatedOrReplayed resolved = transactionTemplate.execute(
				ignored -> resolveConcurrentCreate(command));
			if (resolved == null) {
				throw exception;
			}
			return resolved;
		}
	}

	private CreatedOrReplayed resolveConcurrentCreate(PaymentCommand command) {
		byte[] fingerprint = fingerprint(command.orderId());
		var replay = repository.findByIdempotencyKey(command.idempotencyKey());
		if (replay.isEmpty()) {
			throw new PaymentConflictException(
				"El pedido ya tiene un pago activo o pendiente de revisión.");
		}
		StoredPaymentIntent stored = replay.get();
		if (!stored.orderId().equals(command.orderId())
				|| !MessageDigest.isEqual(stored.requestFingerprint(), fingerprint)) {
			throw new PaymentConflictException(
				"La clave idempotente ya fue usada para otra operación.");
		}
		return new CreatedOrReplayed(stored, true);
	}

	private CreatedOrReplayed createOrReplay(PaymentCommand command) {
		LockedPaymentOrder order = repository.lockOrder(command.orderId())
			.orElseThrow(() -> new InvalidPaymentException("El pedido no existe."));
		byte[] fingerprint = fingerprint(command.orderId());
		var replay = repository.findByIdempotencyKey(command.idempotencyKey());
		if (replay.isPresent()) {
			StoredPaymentIntent stored = replay.get();
			if (!stored.orderId().equals(command.orderId())
					|| !MessageDigest.isEqual(stored.requestFingerprint(), fingerprint)) {
				throw new PaymentConflictException(
					"La clave idempotente ya fue usada para otra operación.");
			}
			return new CreatedOrReplayed(stored, true);
		}
		if (order.status() != OrderStatus.PENDING_CONFIRMATION) {
			throw new PaymentConflictException(
				"El pedido no se encuentra pendiente de pago.");
		}
		if (!order.reservationExpiresAt().isAfter(clock.instant())) {
			throw new PaymentConflictException("La reserva del pedido ya venció.");
		}
		if (repository.hasBlockingIntent(order.internalId())) {
			throw new PaymentConflictException(
				"El pedido ya tiene un pago activo o pendiente de revisión.");
		}

		UUID paymentIntentId = UUID.randomUUID();
		UUID transitionIdempotencyKey = UUID.randomUUID();
		int attemptNumber = repository.nextAttemptNumber(order.internalId());
		repository.insertIntent(
			paymentIntentId,
			order.internalId(),
			command.idempotencyKey(),
			fingerprint,
			transitionIdempotencyKey,
			gateway.provider(),
			attemptNumber,
			order.amount(),
			order.currencyCode(),
			paymentIntentId.toString());
		StoredPaymentIntent created = repository.findByPublicId(paymentIntentId)
			.orElseThrow(() -> new IllegalStateException(
				"No se pudo recuperar el intento de pago."));
		return new CreatedOrReplayed(created, false);
	}

	private StoredPaymentIntent applyGatewayPayment(
			UUID paymentIntentId,
			GatewayPayment rawPayment) {
		GatewayPayment payment = validateGatewayPayment(rawPayment);
		StoredPaymentIntent snapshot = repository.findByPublicId(paymentIntentId)
			.orElseThrow(() -> new InvalidPaymentException(
				"El intento de pago no existe."));
		LockedPaymentOrder order = repository.lockOrder(snapshot.orderId())
			.orElseThrow(() -> new InvalidPaymentException("El pedido no existe."));
		StoredPaymentIntent intent = repository.lockIntent(paymentIntentId)
			.orElseThrow(() -> new InvalidPaymentException(
				"El intento de pago no existe."));
		if (intent.status() != PaymentIntentStatus.CREATED) {
			throw new PaymentConflictException(
				"El intento de pago ya tiene un resultado registrado.");
		}

		var replay = repository.findTransaction(
			intent.provider(),
			payment.providerPaymentId());
		if (replay.isPresent()) {
			requireSameTransaction(replay.get(), intent, payment);
			return intent;
		}
		requireSameCommercialData(intent, payment);
		long transactionId = repository.insertTransaction(
			UUID.randomUUID(),
			intent.internalId(),
			intent.provider(),
			payment);

		if (payment.status() == PaymentResultStatus.PENDING) {
			repository.updateIntentStatus(
				intent.internalId(), intent.version(), intent.status(),
				PaymentIntentStatus.PENDING);
		}
		else if (payment.status() == PaymentResultStatus.REJECTED) {
			repository.updateIntentStatus(
				intent.internalId(), intent.version(), intent.status(),
				PaymentIntentStatus.REJECTED);
		}
		else if (order.status() != OrderStatus.PENDING_CONFIRMATION) {
			repository.updateIntentStatus(
				intent.internalId(), intent.version(), intent.status(),
				PaymentIntentStatus.REQUIRES_REVIEW);
			repository.markTransactionForReview(transactionId);
		}
		else {
			OrderTransitionExecution confirmation =
				orderConfirmer.confirmWithinCurrentTransaction(
					intent.orderId(),
					intent.transitionIdempotencyKey());
			if (confirmation.expired()) {
				repository.updateIntentStatus(
					intent.internalId(), intent.version(), intent.status(),
					PaymentIntentStatus.REQUIRES_REVIEW);
				repository.markTransactionForReview(transactionId);
			}
			else {
				repository.updateIntentStatus(
					intent.internalId(), intent.version(), intent.status(),
					PaymentIntentStatus.APPROVED);
				repository.markTransactionApplied(transactionId, clock.instant());
			}
		}
		return repository.findByPublicId(paymentIntentId)
			.orElseThrow(() -> new IllegalStateException(
				"No se pudo recuperar el resultado del pago."));
	}

	private void markForReviewPreserving(
			UUID paymentIntentId,
			RuntimeException original) {
		try {
			transactionTemplate.executeWithoutResult(
				ignored -> markGatewayFailureForReview(paymentIntentId));
		}
		catch (RuntimeException reviewFailure) {
			original.addSuppressed(reviewFailure);
		}
	}

	private void markGatewayFailureForReview(UUID paymentIntentId) {
		StoredPaymentIntent snapshot = repository.findByPublicId(paymentIntentId)
			.orElseThrow(() -> new InvalidPaymentException(
				"El intento de pago no existe."));
		repository.lockOrder(snapshot.orderId())
			.orElseThrow(() -> new InvalidPaymentException("El pedido no existe."));
		StoredPaymentIntent intent = repository.lockIntent(paymentIntentId)
			.orElseThrow(() -> new InvalidPaymentException(
				"El intento de pago no existe."));
		if (intent.status() == PaymentIntentStatus.CREATED) {
			repository.updateIntentStatus(
				intent.internalId(),
				intent.version(),
				PaymentIntentStatus.CREATED,
				PaymentIntentStatus.REQUIRES_REVIEW);
		}
	}

	private PaymentCommand validate(PaymentCommand command) {
		if (command == null
				|| !isUuidV4(command.orderId())
				|| !isUuidV4(command.idempotencyKey())) {
			throw new InvalidPaymentException(
				"El pedido y la clave idempotente deben ser UUID v4 válidos.");
		}
		return command;
	}

	private GatewayPayment validateGatewayPayment(GatewayPayment payment) {
		if (payment == null
				|| payment.providerPaymentId() == null
				|| payment.providerPaymentId().isBlank()
				|| payment.providerPaymentId().length() > 100
				|| payment.status() == null
				|| payment.amount() == null
				|| payment.amount().signum() <= 0
				|| payment.currencyCode() == null
				|| !payment.currencyCode().matches("[A-Z]{3}")) {
			throw new InvalidPaymentException(
				"El proveedor devolvió un resultado de pago inválido.");
		}
		return new GatewayPayment(
			payment.providerPaymentId().trim(),
			payment.status(),
			payment.amount(),
			payment.currencyCode().toUpperCase(Locale.ROOT));
	}

	private void requireSameCommercialData(
			StoredPaymentIntent intent,
			GatewayPayment payment) {
		if (intent.amount().compareTo(payment.amount()) != 0
				|| !intent.currencyCode().equals(payment.currencyCode())) {
			throw new PaymentConflictException(
				"El pago verificado no coincide con el pedido.");
		}
	}

	private void requireSameTransaction(
			StoredPaymentTransaction stored,
			StoredPaymentIntent intent,
			GatewayPayment payment) {
		if (stored.paymentIntentInternalId() != intent.internalId()
				|| stored.provider() != intent.provider()
				|| stored.status() != payment.status()
				|| stored.amount().compareTo(payment.amount()) != 0
				|| !stored.currencyCode().equals(payment.currencyCode())) {
			throw new PaymentConflictException(
				"El identificador del proveedor pertenece a otra operación.");
		}
	}

	private byte[] fingerprint(UUID orderId) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(("payment:v1:" + orderId)
					.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	private boolean isUuidV4(UUID value) {
		return value != null && value.version() == 4 && value.variant() == 2;
	}

	private record CreatedOrReplayed(
		StoredPaymentIntent intent,
		boolean replayed) {
	}
}
