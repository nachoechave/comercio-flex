package com.comercioflex.payment.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.application.InvalidOrderTransitionException;
import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentResultStatus;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

@Service
public class CheckoutProService {
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckoutProService.class);
	private static final int RECONCILIATION_BATCH_SIZE = 20;
	private static final Duration UNPAID_GRACE = Duration.ofMinutes(5);
	private static final Duration PENDING_SETTLEMENT_LIMIT = Duration.ofHours(24);

	private static final String RETURN_NAMESPACE = "checkout-return:v1:";
	private static final String ROUTE_NAMESPACE = "checkout-webhook-route:v1:";

	private final CheckoutRepository repository;
	private final CheckoutControlRepository controlRepository;
	private final PaymentCredentialResolver credentials;
	private final CheckoutProGateway gateway;
	private final PaidOrderConfirmer orderConfirmer;
	private final RejectedPaymentNotifier rejectedPaymentNotifier;
	private final AdminOrderRepository orders;
	private final TenantResolver tenantResolver;
	private final CheckoutProProperties properties;
	private final PaymentOAuthProperties oauthProperties;
	private final TransactionTemplate tenantTransactions;
	private final TransactionTemplate controlTransactions;
	private final Clock clock;

	@Autowired
	public CheckoutProService(
			CheckoutRepository repository,
			CheckoutControlRepository controlRepository,
			PaymentCredentialResolver credentials,
			CheckoutProGateway gateway,
			PaidOrderConfirmer orderConfirmer,
			RejectedPaymentNotifier rejectedPaymentNotifier,
			AdminOrderRepository orders,
			TenantResolver tenantResolver,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate tenantTransactions,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions) {
		this(repository, controlRepository, credentials, gateway, orderConfirmer,
			rejectedPaymentNotifier, orders,
			tenantResolver, properties, oauthProperties, tenantTransactions,
			controlTransactions, Clock.systemUTC());
	}

	CheckoutProService(
			CheckoutRepository repository,
			CheckoutControlRepository controlRepository,
			PaymentCredentialResolver credentials,
			CheckoutProGateway gateway,
			PaidOrderConfirmer orderConfirmer,
			RejectedPaymentNotifier rejectedPaymentNotifier,
			AdminOrderRepository orders,
			TenantResolver tenantResolver,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			TransactionTemplate tenantTransactions,
			TransactionTemplate controlTransactions,
			Clock clock) {
		this.repository = repository;
		this.controlRepository = controlRepository;
		this.credentials = credentials;
		this.gateway = gateway;
		this.orderConfirmer = orderConfirmer;
		this.rejectedPaymentNotifier = rejectedPaymentNotifier;
		this.orders = orders;
		this.tenantResolver = tenantResolver;
		this.properties = properties;
		this.oauthProperties = oauthProperties;
		this.tenantTransactions = tenantTransactions;
		this.controlTransactions = controlTransactions;
		this.clock = clock;
	}

	public CheckoutInitiation initiate(
			String tenantSlug, UUID orderId, String lookupToken, UUID idempotencyKey) {
		requireEnabled();
		validateUuidV4(orderId, "El pedido no es válido.");
		validateUuidV4(idempotencyKey, "Idempotency-Key debe ser un UUID v4 válido.");
		if (lookupToken == null || !lookupToken.matches("^[A-Za-z0-9_-]{43}$")) {
			throw notFound();
		}
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		controlTransactions.executeWithoutResult(status ->
			controlRepository.requireCommerciallyEnabled(tenant.id(), environment()));
		PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());

		PreparedAttempt prepared;
		try {
			prepared = tenantTransactions.execute(status -> prepare(
				orderId, sha256(lookupToken), idempotencyKey));
		}
		catch (DataIntegrityViolationException exception) {
			prepared = tenantTransactions.execute(status -> replay(idempotencyKey, orderId));
		}
		PreparedAttempt resolved = Objects.requireNonNull(prepared);
		if (resolved.replayed()) {
			return initiation(resolved.attempt(), true);
		}

		String returnToken = deterministicToken(RETURN_NAMESPACE, idempotencyKey);
		String routeToken = deterministicToken(ROUTE_NAMESPACE, idempotencyKey);
		UUID routeId = UUID.randomUUID();
		controlTransactions.executeWithoutResult(status -> controlRepository.insertRoute(
			routeId, sha256(routeToken), tenant.id(), environment(),
			resolved.attempt().id(), credential.sellerAccountId(),
			resolved.attempt().reservationExpiresAt()));

		CheckoutPreferenceCommand command = new CheckoutPreferenceCommand(
			resolved.attempt().id(), resolved.attempt().externalReference(),
			"Pedido #" + resolved.attempt().orderNumber(), resolved.attempt().amount(),
			resolved.attempt().currencyCode(), returnUri(tenant.slug(), returnToken),
			notificationUri(routeToken), resolved.attempt().reservationExpiresAt());
		CreatedCheckoutPreference preference;
		try {
			preference = gateway.createPreference(credential, command);
			validatePreference(preference, credential);
		}
		catch (RuntimeException exception) {
			markCreationForReview(resolved.attempt(), exception);
			controlTransactions.executeWithoutResult(status ->
				controlRepository.expireRoute(resolved.attempt().id(), environment()));
			throw exception;
		}

		StoredCheckoutAttempt attached = tenantTransactions.execute(status -> {
			StoredCheckoutAttempt locked = repository.findByPublicId(
				resolved.attempt().id(), true).orElseThrow(this::notFound);
			repository.attachPreference(
				locked, preference.preferenceId(), preference.checkoutUri(),
				locked.reservationExpiresAt(), credential.sellerAccountId(),
				credential.environment(), clock.instant());
			return repository.findByPublicId(locked.id(), false).orElseThrow(this::notFound);
		});
		controlTransactions.executeWithoutResult(status -> controlRepository.activateRoute(
			resolved.attempt().id(), environment(), preference.preferenceId()));
		return initiation(Objects.requireNonNull(attached), false);
	}

	public PaymentReturnView findReturn(String returnToken) {
		requireEnabled();
		validateReturnToken(returnToken);
		return Objects.requireNonNull(tenantTransactions.execute(status -> {
			StoredCheckoutAttempt attempt = requireReturnAttempt(returnToken);
			String latest = repository.latestProviderStatus(attempt.internalId());
			String paymentStatus = attempt.orderStatus().equals(OrderStatus.EXPIRED.name())
					&& attempt.status() != PaymentIntentStatus.APPROVED
					&& attempt.status() != PaymentIntentStatus.REQUIRES_REVIEW
				? "EXPIRED" : switch (attempt.status()) {
				case APPROVED -> "APPROVED";
				case REQUIRES_REVIEW -> "REQUIRES_REVIEW";
				case REJECTED -> "REJECTED";
				case EXPIRED -> "EXPIRED";
				default -> latest == null ? attempt.status().name() : latest;
			};
			boolean canRetry = (attempt.status() == PaymentIntentStatus.PENDING
					|| attempt.status() == PaymentIntentStatus.REJECTED)
				&& attempt.orderStatus().equals(OrderStatus.PENDING_CONFIRMATION.name())
				&& attempt.reservationExpiresAt().isAfter(clock.instant());
			return new PaymentReturnView(
				attempt.orderId(), attempt.orderNumber(), attempt.orderStatus(),
				paymentStatus, null, canRetry, attempt.updatedAt());
		}));
	}

	public PaymentReturnView inspectReturn(String tenantSlug, String returnToken) {
		requireEnabled();
		validateReturnToken(returnToken);
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		StoredCheckoutAttempt attempt = Objects.requireNonNull(tenantTransactions.execute(
			status -> requireReturnAttempt(returnToken)));
		if (attempt.status() != PaymentIntentStatus.PENDING) {
			return findReturn(returnToken);
		}
		PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());
		validateCredential(attempt, credential);
		ProviderCheckoutState providerState = gateway.inspectPreference(
			credential, attempt.preferenceId(), attempt.externalReference());
		PaymentReturnView current = findReturn(returnToken);
		if (providerState != ProviderCheckoutState.NO_PAYMENT_RECORDED) {
			return current;
		}
		return new PaymentReturnView(
			current.orderId(), current.orderNumber(), current.orderStatus(),
			current.paymentStatus(), PaymentReturnOutcome.PAYMENT_NOT_RECORDED,
			current.canRetry(), current.updatedAt());
	}

	public PaymentReturnView reconcileReturn(
			String tenantSlug, String returnToken, String providerPaymentId) {
		requireEnabled();
		validateReturnToken(returnToken);
		if (providerPaymentId == null || !providerPaymentId.matches("^[0-9]{1,20}$")) {
			throw new CheckoutPaymentException(
				"INVALID_PROVIDER_RESOURCE", "El identificador de pago no es vÃ¡lido.");
		}
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		StoredCheckoutAttempt attempt = Objects.requireNonNull(tenantTransactions.execute(
			status -> requireReturnAttempt(returnToken)));
		if (attempt.status() == PaymentIntentStatus.APPROVED
				|| attempt.status() == PaymentIntentStatus.REJECTED
				|| attempt.status() == PaymentIntentStatus.EXPIRED
				|| attempt.status() == PaymentIntentStatus.REQUIRES_REVIEW) {
			return findReturn(returnToken);
		}
		PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());
		validateCredential(attempt, credential);
		VerifiedProviderPayment payment = gateway.findPayment(credential, providerPaymentId);
		applyVerifiedPayment(attempt.id(), payment);
		return findReturn(returnToken);
	}

	public PaymentReturnView reconcileReturn(String tenantSlug, String returnToken) {
		requireEnabled();
		validateReturnToken(returnToken);
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		StoredCheckoutAttempt attempt = Objects.requireNonNull(tenantTransactions.execute(
			status -> requireReturnAttempt(returnToken)));
		if (attempt.status() != PaymentIntentStatus.PENDING) {
			return findReturn(returnToken);
		}
		PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());
		validateCredential(attempt, credential);
		var payment = gateway.findPaymentForPreference(
			credential, attempt.preferenceId(), attempt.externalReference(),
			attempt.amount(), attempt.currencyCode());
		if (payment.isPresent()) {
			applyVerifiedPayment(attempt.id(), payment.get());
			return findReturn(returnToken);
		}
		PaymentReturnView current = findReturn(returnToken);
		return new PaymentReturnView(
			current.orderId(), current.orderNumber(), current.orderStatus(),
			current.paymentStatus(), PaymentReturnOutcome.PAYMENT_NOT_RECORDED,
			current.canRetry(), current.updatedAt());
	}

	public boolean reconcilePendingOrder(
			String tenantSlug, UUID orderId, String lookupToken) {
		requireEnabled();
		validateUuidV4(orderId, "El pedido no es válido.");
		if (lookupToken == null || !lookupToken.matches("^[A-Za-z0-9_-]{43}$")) {
			throw notFound();
		}
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		StoredCheckoutAttempt attempt = tenantTransactions.execute(status ->
			repository.findPendingByOrder(orderId, sha256(lookupToken)).orElse(null));
		if (attempt == null) return false;

		PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());
		validateCredential(attempt, credential);
		var payment = gateway.findPaymentForPreference(
			credential, attempt.preferenceId(), attempt.externalReference(),
			attempt.amount(), attempt.currencyCode());
		if (payment.isEmpty()) return false;
		applyVerifiedPayment(attempt.id(), payment.get());
		return true;
	}

	public PaymentReturnView reconcilePrivateOrder(
			String tenantSlug, UUID orderId, String lookupToken) {
		requireEnabled();
		validateUuidV4(orderId, "El pedido no es válido.");
		if (lookupToken == null || !lookupToken.matches("^[A-Za-z0-9_-]{43}$")) {
			throw notFound();
		}
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		StoredCheckoutAttempt attempt = tenantTransactions.execute(status ->
			repository.findLatestByOrder(orderId, sha256(lookupToken)).orElse(null));
		if (attempt == null) return null;
		if (attempt.status() == PaymentIntentStatus.PENDING) {
			PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());
			validateCredential(attempt, credential);
			gateway.findPaymentForPreference(
				credential, attempt.preferenceId(), attempt.externalReference(),
				attempt.amount(), attempt.currencyCode())
				.ifPresent(payment -> applyVerifiedPayment(attempt.id(), payment));
		}
		StoredCheckoutAttempt current = Objects.requireNonNull(tenantTransactions.execute(status ->
			repository.findByPublicId(attempt.id(), false).orElseThrow(this::notFound)));
		return Objects.requireNonNull(tenantTransactions.execute(status -> view(current)));
	}

	public int reconcilePendingTenant(long tenantId, String tenantSlug) {
		if (!properties.enabled()) return 0;
		PaymentCredential credential = credentials.resolve(tenantId, tenantSlug);
		List<StoredCheckoutAttempt> attempts = Objects.requireNonNull(tenantTransactions.execute(
			status -> repository.findPendingForReconciliation(RECONCILIATION_BATCH_SIZE)));
		int processed = 0;
		for (StoredCheckoutAttempt attempt : attempts) {
			String stage = "CREDENTIAL_RESOLUTION";
			try {
				validateCredential(attempt, credential);
				stage = "PROVIDER_SEARCH";
				var payment = gateway.findPaymentForPreference(
					credential, attempt.preferenceId(), attempt.externalReference(),
					attempt.amount(), attempt.currencyCode());
				if (payment.isPresent()) {
					stage = "PAYMENT_APPLICATION";
					applyVerifiedPayment(attempt.id(), payment.get());
					processed++;
					if (payment.get().status() == PaymentResultStatus.PENDING
							&& expiredBeyond(attempt, PENDING_SETTLEMENT_LIMIT)) {
						expirePendingAttempt(attempt.id());
					}
				}
				else if (expiredBeyond(attempt, UNPAID_GRACE)) {
					expirePendingAttempt(attempt.id());
					processed++;
				}
			}
			catch (RuntimeException exception) {
				logReconciliationFailure(tenantSlug, attempt.id(), stage, exception);
			}
		}
		return processed;
	}

	public void applyVerifiedPayment(
			UUID paymentAttemptId, VerifiedProviderPayment payment) {
		try {
			tenantTransactions.executeWithoutResult(status ->
				applyVerifiedInsideTransaction(paymentAttemptId, payment, false));
		}
		catch (InvalidOrderTransitionException exception) {
			tenantTransactions.executeWithoutResult(status ->
				applyVerifiedInsideTransaction(paymentAttemptId, payment, true));
		}
	}

	private void applyVerifiedInsideTransaction(
			UUID paymentAttemptId, VerifiedProviderPayment payment,
			boolean forceReview) {
			StoredCheckoutAttempt attempt = repository.findByPublicId(paymentAttemptId, true)
				.orElseThrow(this::notFound);
			validatePayment(attempt, payment);
			boolean applied = false;
			boolean review = forceReview
				|| attempt.status() == PaymentIntentStatus.REQUIRES_REVIEW;
			if (payment.status() == PaymentResultStatus.APPROVED && !review) {
				if (attempt.status() == PaymentIntentStatus.APPROVED) {
					applied = true;
				}
				else if (attempt.status() == PaymentIntentStatus.PENDING) {
					OrderTransitionExecution confirmation = orderConfirmer
						.confirmWithinCurrentTransaction(
							attempt.orderId(), attempt.transitionIdempotencyKey(), "Mercado Pago");
					applied = !confirmation.expired();
					review = confirmation.expired();
				}
				else {
					review = true;
				}
			}
			Instant now = clock.instant();
			repository.applyVerifiedPayment(attempt, payment, applied, review, now);
			if (payment.status() == PaymentResultStatus.REJECTED) {
				rejectedPaymentNotifier.notifyWithinCurrentTransaction(
					attempt.orderId(), attempt.id(), now);
				if (!attempt.reservationExpiresAt().isAfter(now)) {
					orders.expireOrder(attempt.orderInternalId());
				}
			}
	}

	private void expirePendingAttempt(UUID paymentAttemptId) {
		tenantTransactions.executeWithoutResult(status -> {
			StoredCheckoutAttempt attempt = repository.findByPublicId(paymentAttemptId, true)
				.orElseThrow(this::notFound);
			if (attempt.status() != PaymentIntentStatus.PENDING) return;
			repository.markExpired(attempt, clock.instant());
			orders.expireOrder(attempt.orderInternalId());
		});
	}

	private boolean expiredBeyond(StoredCheckoutAttempt attempt, Duration delay) {
		return !attempt.checkoutExpiresAt().plus(delay).isAfter(clock.instant());
	}

	private PaymentReturnView view(StoredCheckoutAttempt attempt) {
		String latest = repository.latestProviderStatus(attempt.internalId());
		String paymentStatus = switch (attempt.status()) {
			case APPROVED -> "APPROVED";
			case REJECTED -> "REJECTED";
			case EXPIRED -> "EXPIRED";
			case REQUIRES_REVIEW -> "REQUIRES_REVIEW";
			default -> latest == null ? attempt.status().name() : latest;
		};
		boolean canRetry = (attempt.status() == PaymentIntentStatus.PENDING
				|| attempt.status() == PaymentIntentStatus.REJECTED)
			&& attempt.orderStatus().equals(OrderStatus.PENDING_CONFIRMATION.name())
			&& attempt.reservationExpiresAt().isAfter(clock.instant());
		return new PaymentReturnView(
			attempt.orderId(), attempt.orderNumber(), attempt.orderStatus(),
			paymentStatus, null, canRetry, attempt.updatedAt());
	}

	private PreparedAttempt prepare(
			UUID orderId, byte[] lookupTokenHash, UUID idempotencyKey) {
		CheckoutOrder order = repository.lockOrder(orderId, lookupTokenHash)
			.orElseThrow(this::notFound);
		byte[] fingerprint = sha256("checkout-pro:v1:" + orderId);
		var existing = repository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), fingerprint, orderId);
		}
		if (order.status() != OrderStatus.PENDING_CONFIRMATION
				|| !order.reservationExpiresAt().isAfter(clock.instant())) {
			throw new CheckoutPaymentException(
				"ORDER_NOT_PAYABLE", "El pedido ya no se encuentra disponible para pagar.");
		}
		if (repository.hasBlockingIntent(order.internalId())) {
			throw new CheckoutPaymentException(
				"PAYMENT_ALREADY_IN_PROGRESS", "El pedido ya tiene un pago en proceso.");
		}
		UUID attemptId = UUID.randomUUID();
		repository.insertIntent(
			attemptId, order.internalId(), idempotencyKey, fingerprint, UUID.randomUUID(),
			sha256(deterministicToken(RETURN_NAMESPACE, idempotencyKey)),
			clock.instant().plus(properties.returnTokenTtl()),
			repository.nextAttemptNumber(order.internalId()), order.amount(),
			order.currencyCode());
		return new PreparedAttempt(
			repository.findByPublicId(attemptId, false).orElseThrow(this::notFound), false);
	}

	private PreparedAttempt replay(UUID idempotencyKey, UUID orderId) {
		StoredCheckoutAttempt attempt = repository.findByIdempotencyKey(idempotencyKey)
			.orElseThrow(() -> new CheckoutPaymentException(
				"PAYMENT_ALREADY_IN_PROGRESS", "El pedido ya tiene un pago en proceso."));
		return replay(attempt, sha256("checkout-pro:v1:" + orderId), orderId);
	}

	private PreparedAttempt replay(
			StoredCheckoutAttempt attempt, byte[] fingerprint, UUID orderId) {
		if (!attempt.orderId().equals(orderId)
				|| !MessageDigest.isEqual(attempt.requestFingerprint(), fingerprint)) {
			throw new CheckoutPaymentException(
				"IDEMPOTENCY_CONFLICT", "La clave idempotente ya fue usada.");
		}
		if (attempt.checkoutUri() == null || attempt.checkoutExpiresAt() == null) {
			throw new CheckoutPaymentException(
				"PAYMENT_REQUIRES_REVIEW", "El intento de pago requiere revisión.");
		}
		return new PreparedAttempt(attempt, true);
	}

	private CheckoutInitiation initiation(StoredCheckoutAttempt attempt, boolean replayed) {
		return new CheckoutInitiation(
			attempt.checkoutUri(), attempt.id(), attempt.checkoutExpiresAt(), replayed);
	}

	private void validatePreference(
			CreatedCheckoutPreference preference, PaymentCredential credential) {
		if (preference == null || blank(preference.preferenceId())
				|| preference.checkoutUri() == null
				|| !preference.checkoutUri().isAbsolute()
				|| !credential.sellerAccountId().equals(preference.collectorAccountId())) {
			throw new CheckoutPaymentException(
				"PREFERENCE_VALIDATION_FAILED", "La preferencia no pertenece a la cuenta esperada.");
		}
	}

	private void validatePayment(
			StoredCheckoutAttempt attempt, VerifiedProviderPayment payment) {
		if (payment == null) {
			throw paymentValidationFailure("PAYMENT_SELECTION", "INVALID_PROVIDER_RESPONSE");
		}
		if (!attempt.sellerAccountId().equals(payment.sellerAccountId())) {
			throw paymentValidationFailure("SELLER_VALIDATION", "SELLER_MISMATCH");
		}
		if (attempt.environment() == PaymentEnvironment.PRODUCTION && !payment.liveMode()) {
			throw paymentValidationFailure("ENVIRONMENT_VALIDATION", "ENVIRONMENT_MISMATCH");
		}
		if (!attempt.preferenceId().equals(payment.preferenceId())) {
			throw paymentValidationFailure("PREFERENCE_VALIDATION", "PREFERENCE_MISMATCH");
		}
		if (!attempt.externalReference().equals(payment.externalReference())) {
			throw paymentValidationFailure("REFERENCE_VALIDATION", "REFERENCE_MISMATCH");
		}
		if (attempt.amount().compareTo(payment.amount()) != 0) {
			throw paymentValidationFailure("AMOUNT_VALIDATION", "AMOUNT_MISMATCH");
		}
		if (!attempt.currencyCode().equals(payment.currencyCode())) {
			throw paymentValidationFailure("CURRENCY_VALIDATION", "CURRENCY_MISMATCH");
		}
	}

	private CheckoutPaymentException paymentValidationFailure(String stage, String reason) {
		return new CheckoutPaymentException(
			"PAYMENT_VALIDATION_FAILED", "El pago verificado no coincide con el pedido.")
			.withReconciliationDiagnostics(stage, reason, null, null, null);
	}

	private void markCreationForReview(
			StoredCheckoutAttempt attempt, RuntimeException original) {
		try {
			tenantTransactions.executeWithoutResult(status -> repository
				.findByPublicId(attempt.id(), true)
				.filter(item -> item.status() == PaymentIntentStatus.CREATED)
				.ifPresent(repository::markCreationForReview));
		}
		catch (RuntimeException failure) {
			original.addSuppressed(failure);
		}
	}

	private URI returnUri(String tenantSlug, String token) {
		return properties.frontendBaseUri().resolve(
			"/stores/" + tenantSlug + "/payment-return/" + token);
	}

	private URI notificationUri(String routeToken) {
		return properties.publicBackendBaseUri().resolve(
			"/api/v1/integrations/mercado-pago/webhooks?route=" + routeToken
				+ "&source_news=webhooks");
	}

	private PaymentEnvironment environment() {
		return oauthProperties.environment();
	}

	private void requireEnabled() {
		if (!properties.enabled()) {
			throw new CheckoutPaymentException(
				"PAYMENTS_NOT_ENABLED", "Los pagos en línea no están habilitados.");
		}
	}

	private String deterministicToken(String namespace, UUID idempotencyKey) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
				properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				mac.doFinal((namespace + idempotencyKey).getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("HMAC-SHA256 no está disponible.", exception);
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

	private void validateUuidV4(UUID value, String message) {
		if (value == null || value.version() != 4 || value.variant() != 2) {
			throw new CheckoutPaymentException("INVALID_PAYMENT_REQUEST", message);
		}
	}

	private CheckoutPaymentException notFound() {
		return new CheckoutPaymentException("PAYMENT_NOT_FOUND", "El pago no existe.");
	}

	private void validateCredential(
			StoredCheckoutAttempt attempt, PaymentCredential credential) {
		if (!attempt.sellerAccountId().equals(credential.sellerAccountId())) {
			throw credentialMismatch("SELLER_VALIDATION", "SELLER_MISMATCH");
		}
		if (attempt.environment() != credential.environment()
				|| credential.environment() != environment()) {
			throw credentialMismatch("ENVIRONMENT_VALIDATION", "ENVIRONMENT_MISMATCH");
		}
	}

	private CheckoutPaymentException credentialMismatch(String stage, String reason) {
		return new CheckoutPaymentException(
			"PAYMENT_CREDENTIAL_MISMATCH", "La credencial no coincide con el pago.")
			.withReconciliationDiagnostics(stage, reason, null, null, null);
	}

	private void logReconciliationFailure(
			String tenantSlug, UUID attemptId, String fallbackStage,
			RuntimeException exception) {
		CheckoutPaymentException paymentException = exception instanceof CheckoutPaymentException current
			? current : null;
		CheckoutPaymentException.ReconciliationDiagnostics diagnostics = paymentException == null
			? null : paymentException.reconciliationDiagnostics();
		String stage = diagnostics == null ? fallbackStage : diagnostics.stage();
		String reason = diagnostics == null
			? (paymentException == null ? "APPLICATION_FAILED" : paymentException.code())
			: diagnostics.reason();
		Integer providerHttpStatus = diagnostics == null
			? null : diagnostics.providerHttpStatus();
		String providerErrorCode = diagnostics == null
			? null : diagnostics.providerErrorCode();
		Integer resultCount = diagnostics == null ? null : diagnostics.resultCount();
		Boolean providerResponseNull = diagnostics == null
			? null : diagnostics.providerResponseNull();
		Boolean merchantOrdersCollectionNull = diagnostics == null
			? null : diagnostics.merchantOrdersCollectionNull();
		Integer merchantOrdersCount = diagnostics == null
			? null : diagnostics.merchantOrdersCount();
		Boolean pagingPresent = diagnostics == null ? null : diagnostics.pagingPresent();
		LOGGER.warn(
			"event=payment_reconciliation_failed tenant={} attempt={} stage={} reason={} "
				+ "providerHttpStatus={} providerErrorCode={} resultCount={} "
				+ "providerResponseNull={} merchantOrdersCollectionNull={} "
				+ "merchantOrdersCount={} pagingPresent={}",
			tenantSlug, attemptId, stage, reason, providerHttpStatus,
			providerErrorCode, resultCount, providerResponseNull,
			merchantOrdersCollectionNull, merchantOrdersCount, pagingPresent);
	}

	private StoredCheckoutAttempt requireReturnAttempt(String returnToken) {
		StoredCheckoutAttempt attempt = repository.findByReturnTokenHash(sha256(returnToken))
			.orElseThrow(this::notFound);
		if (!attempt.returnTokenExpiresAt().isAfter(clock.instant())) {
			throw notFound();
		}
		return attempt;
	}

	private void validateReturnToken(String returnToken) {
		if (returnToken == null || !returnToken.matches("^[A-Za-z0-9_-]{43}$")) {
			throw notFound();
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private record PreparedAttempt(StoredCheckoutAttempt attempt, boolean replayed) {
	}
}
