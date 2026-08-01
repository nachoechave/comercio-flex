package com.comercioflex.payment.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.application.InvalidOrderTransitionException;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentResultStatus;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

@Service
public class CheckoutProService {

	private static final String RETURN_NAMESPACE = "checkout-return:v1:";
	private static final String ROUTE_NAMESPACE = "checkout-webhook-route:v1:";

	private final CheckoutRepository repository;
	private final CheckoutControlRepository controlRepository;
	private final PaymentCredentialResolver credentials;
	private final CheckoutProGateway gateway;
	private final PaidOrderConfirmer orderConfirmer;
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
			TenantResolver tenantResolver,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate tenantTransactions,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions) {
		this(repository, controlRepository, credentials, gateway, orderConfirmer,
			tenantResolver, properties, oauthProperties, tenantTransactions,
			controlTransactions, Clock.systemUTC());
	}

	CheckoutProService(
			CheckoutRepository repository,
			CheckoutControlRepository controlRepository,
			PaymentCredentialResolver credentials,
			CheckoutProGateway gateway,
			PaidOrderConfirmer orderConfirmer,
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
		if (returnToken == null || !returnToken.matches("^[A-Za-z0-9_-]{43}$")) {
			throw notFound();
		}
		return Objects.requireNonNull(tenantTransactions.execute(status -> {
			StoredCheckoutAttempt attempt = repository.findByReturnTokenHash(sha256(returnToken))
				.orElseThrow(this::notFound);
			if (!attempt.returnTokenExpiresAt().isAfter(clock.instant())) {
				throw notFound();
			}
			String latest = repository.latestProviderStatus(attempt.internalId());
			String paymentStatus = attempt.orderStatus().equals(OrderStatus.EXPIRED.name())
					&& attempt.status() != PaymentIntentStatus.APPROVED
					&& attempt.status() != PaymentIntentStatus.REQUIRES_REVIEW
				? "EXPIRED" : switch (attempt.status()) {
				case APPROVED -> "APPROVED";
				case REQUIRES_REVIEW -> "REQUIRES_REVIEW";
				case REJECTED -> "REJECTED";
				default -> latest == null ? attempt.status().name() : latest;
			};
			boolean canRetry = attempt.status() == PaymentIntentStatus.PENDING
				&& attempt.orderStatus().equals(OrderStatus.PENDING_CONFIRMATION.name())
				&& attempt.reservationExpiresAt().isAfter(clock.instant());
			return new PaymentReturnView(
				attempt.orderId(), attempt.orderNumber(), attempt.orderStatus(),
				paymentStatus, canRetry, attempt.updatedAt());
		}));
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
							attempt.orderId(), attempt.transitionIdempotencyKey());
					applied = !confirmation.expired();
					review = confirmation.expired();
				}
				else {
					review = true;
				}
			}
			repository.applyVerifiedPayment(attempt, payment, applied, review, clock.instant());
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
		boolean expectedLive = attempt.environment() == PaymentEnvironment.PRODUCTION;
		if (payment == null
				|| !attempt.sellerAccountId().equals(payment.sellerAccountId())
				|| !attempt.preferenceId().equals(payment.preferenceId())
				|| !attempt.externalReference().equals(payment.externalReference())
				|| attempt.amount().compareTo(payment.amount()) != 0
				|| !attempt.currencyCode().equals(payment.currencyCode())
				|| payment.liveMode() != expectedLive) {
			throw new CheckoutPaymentException(
				"PAYMENT_VALIDATION_FAILED", "El pago verificado no coincide con el pedido.");
		}
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

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private record PreparedAttempt(StoredCheckoutAttempt attempt, boolean replayed) {
	}
}
