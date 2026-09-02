package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.application.InvalidOrderTransitionException;
import com.comercioflex.order.application.OrderTransitionExecution;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.payment.domain.PaymentResultStatus;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

@Service
public class QrOrderService {

	private static final Duration CREATION_LEASE = Duration.ofSeconds(30);
	private static final Duration EXPIRATION_SAFETY = Duration.ofSeconds(5);
	private static final Duration MINIMUM_PROVIDER_EXPIRATION = Duration.ofSeconds(30);

	private final QrOrderRepository repository;
	private final CheckoutRepository paymentTransactions;
	private final QrOrderControlRepository controlRepository;
	private final CheckoutControlRepository capabilities;
	private final QrSetupRepository setupRepository;
	private final PaymentCredentialResolver credentials;
	private final MercadoPagoQrOrderGateway gateway;
	private final PaidOrderConfirmer orderConfirmer;
	private final AdminOrderRepository orders;
	private final TenantResolver tenantResolver;
	private final PaymentOAuthProperties oauthProperties;
	private final CheckoutProProperties paymentProperties;
	private final TransactionTemplate tenantTransactions;
	private final TransactionTemplate controlTransactions;
	private final Clock clock;

	@Autowired
	public QrOrderService(
			QrOrderRepository repository,
			CheckoutRepository paymentTransactions,
			QrOrderControlRepository controlRepository,
			CheckoutControlRepository capabilities,
			QrSetupRepository setupRepository,
			PaymentCredentialResolver credentials,
			MercadoPagoQrOrderGateway gateway,
			PaidOrderConfirmer orderConfirmer,
			AdminOrderRepository orders,
			TenantResolver tenantResolver,
			PaymentOAuthProperties oauthProperties,
			CheckoutProProperties paymentProperties,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate tenantTransactions,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions) {
		this(repository, paymentTransactions, controlRepository, capabilities,
			setupRepository, credentials, gateway, orderConfirmer, orders,
			tenantResolver, oauthProperties, paymentProperties, tenantTransactions,
			controlTransactions, Clock.systemUTC());
	}

	QrOrderService(
			QrOrderRepository repository,
			CheckoutRepository paymentTransactions,
			QrOrderControlRepository controlRepository,
			CheckoutControlRepository capabilities,
			QrSetupRepository setupRepository,
			PaymentCredentialResolver credentials,
			MercadoPagoQrOrderGateway gateway,
			PaidOrderConfirmer orderConfirmer,
			AdminOrderRepository orders,
			TenantResolver tenantResolver,
			PaymentOAuthProperties oauthProperties,
			CheckoutProProperties paymentProperties,
			TransactionTemplate tenantTransactions,
			TransactionTemplate controlTransactions,
			Clock clock) {
		this.repository = repository;
		this.paymentTransactions = paymentTransactions;
		this.controlRepository = controlRepository;
		this.capabilities = capabilities;
		this.setupRepository = setupRepository;
		this.credentials = credentials;
		this.gateway = gateway;
		this.orderConfirmer = orderConfirmer;
		this.orders = orders;
		this.tenantResolver = tenantResolver;
		this.oauthProperties = oauthProperties;
		this.paymentProperties = paymentProperties;
		this.tenantTransactions = tenantTransactions;
		this.controlTransactions = controlTransactions;
		this.clock = clock;
	}

	public QrOrderInitiation initiate(
			String tenantSlug, UUID orderId, String lookupToken, UUID idempotencyKey) {
		requireEnabled();
		validateInput(orderId, lookupToken, idempotencyKey);
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		PaymentCredential credential = productionCredential(tenant);
		StoredQrSetup setup = readySetup(tenant.id());
		controlTransactions.executeWithoutResult(status ->
			capabilities.requireCommerciallyEnabled(tenant.id(), environment()));

		Prepared prepared;
		try {
			prepared = Objects.requireNonNull(tenantTransactions.execute(status ->
				prepare(orderId, sha256(lookupToken), idempotencyKey,
					credential, setup.externalPosId())));
		}
		catch (DataIntegrityViolationException exception) {
			prepared = Objects.requireNonNull(tenantTransactions.execute(status ->
				replay(idempotencyKey, orderId)));
		}
		if (!prepared.createAtProvider()) {
			return initiation(prepared.attempt(), true);
		}

		StoredQrOrderAttempt attempt = prepared.attempt();
		Duration expiration = durationUntil(attempt.providerExpiresAt());
		ProviderQrOrder provider;
		try {
			provider = gateway.createOrder(credential, new CreateQrOrderCommand(
				attempt.providerIdempotencyKey(), attempt.externalReference(),
				attempt.externalPosId(), attempt.amount(), attempt.currencyCode(), expiration));
			validateProvider(attempt, credential, provider, true);
		}
		catch (RuntimeException exception) {
			tenantTransactions.executeWithoutResult(status ->
				repository.findByPublicId(attempt.id(), true)
					.filter(current -> current.providerOrderId() == null)
					.ifPresent(current -> repository.markCreationFailed(current, clock.instant())));
			throw exception;
		}

		StoredQrOrderAttempt attached = Objects.requireNonNull(tenantTransactions.execute(status -> {
			StoredQrOrderAttempt current = repository.findByPublicId(attempt.id(), true)
				.orElseThrow(this::notFound);
			if (current.providerOrderId() == null) {
				repository.attachProviderOrder(
					current, provider.orderId(), provider.qrData(), provider.status(),
					current.providerExpiresAt(), clock.instant());
			}
			else if (!current.providerOrderId().equals(provider.orderId())) {
				throw invalid("QR_PROVIDER_ORDER_CONFLICT",
					"Mercado Pago devolvió otra orden para el mismo intento.");
			}
			return repository.findByPublicId(attempt.id(), false).orElseThrow(this::notFound);
		}));
		ensureRoute(tenant, attached);
		return initiation(attached, prepared.replayed());
	}

	public Optional<QrOrderInitiation> findCurrent(
			String tenantSlug, UUID orderId, String lookupToken) {
		requireEnabled();
		validateOrderToken(orderId, lookupToken);
		ResolvedTenant tenant = tenantResolver.resolveActive(tenantSlug);
		StoredQrOrderAttempt attempt = tenantTransactions.execute(status ->
			repository.findCurrentByOrder(orderId, sha256(lookupToken)).orElse(null));
		if (attempt == null) return Optional.empty();
		if ((attempt.status() == PaymentIntentStatus.CREATED
				|| attempt.status() == PaymentIntentStatus.PENDING)
				&& !attempt.providerExpiresAt().isAfter(clock.instant())) {
			UUID attemptId = attempt.id();
			tenantTransactions.executeWithoutResult(status -> {
				StoredQrOrderAttempt current = repository.findByPublicId(attemptId, true)
					.orElseThrow(this::notFound);
				if (current.status() == PaymentIntentStatus.CREATED
						|| current.status() == PaymentIntentStatus.PENDING) {
					repository.updateIntentStatus(current, PaymentIntentStatus.EXPIRED, clock.instant());
				}
			});
			attempt = Objects.requireNonNull(tenantTransactions.execute(status ->
				repository.findByPublicId(attemptId, false).orElseThrow(this::notFound)));
		}
		if (attempt.providerOrderId() != null) ensureRoute(tenant, attempt);
		return Optional.of(initiation(attempt, true));
	}

	public QrOrderProcessingResult fetchAndApply(
			QrOrderRoute route, PaymentCredential credential) {
		if (route.environment() != credential.environment()
				|| !route.expectedSellerAccountId().equals(credential.sellerAccountId())) {
			throw invalid("QR_ROUTE_CREDENTIAL_MISMATCH",
				"La credencial no coincide con la orden QR.");
		}
		ProviderQrOrder provider = gateway.getOrder(credential, route.providerOrderId());
		return applyProviderOrder(route, credential, provider);
	}

	public QrOrderProcessingResult applyProviderOrder(
			QrOrderRoute route, PaymentCredential credential, ProviderQrOrder provider) {
		try {
			return Objects.requireNonNull(tenantTransactions.execute(status ->
				applyInsideTransaction(route, credential, provider, false)));
		}
		catch (InvalidOrderTransitionException exception) {
			return Objects.requireNonNull(tenantTransactions.execute(status ->
				applyInsideTransaction(route, credential, provider, true)));
		}
	}

	private QrOrderProcessingResult applyInsideTransaction(
			QrOrderRoute route, PaymentCredential credential,
			ProviderQrOrder provider, boolean forceReview) {
		StoredQrOrderAttempt attempt = repository.findByPublicId(route.paymentAttemptId(), true)
			.orElseThrow(this::notFound);
		validateProvider(attempt, credential, provider, false);
		repository.updateProviderStatus(attempt, provider.status(), clock.instant());
		String status = provider.status().toLowerCase(Locale.ROOT);
		if (status.equals("created")) return QrOrderProcessingResult.PENDING;
		if (status.equals("expired")) {
			if (attempt.status() == PaymentIntentStatus.CREATED
					|| attempt.status() == PaymentIntentStatus.PENDING) {
				repository.updateIntentStatus(attempt, PaymentIntentStatus.EXPIRED, clock.instant());
			}
			return QrOrderProcessingResult.EXPIRED;
		}
		if (status.equals("canceled")) {
			if (attempt.status() == PaymentIntentStatus.CREATED
					|| attempt.status() == PaymentIntentStatus.PENDING) {
				repository.updateIntentStatus(attempt, PaymentIntentStatus.REJECTED, clock.instant());
			}
			return QrOrderProcessingResult.CANCELED;
		}
		if (status.equals("refunded")) {
			if (attempt.status() == PaymentIntentStatus.CREATED
					|| attempt.status() == PaymentIntentStatus.PENDING) {
				repository.updateIntentStatus(
					attempt, PaymentIntentStatus.REQUIRES_REVIEW, clock.instant());
			}
			return QrOrderProcessingResult.REQUIRES_REVIEW;
		}
		if (!status.equals("processed")) {
			throw invalid("QR_PROVIDER_STATUS_UNSUPPORTED",
				"Mercado Pago devolvió un estado QR no soportado.");
		}
		validateAccreditedPayment(provider);
		boolean applied = attempt.status() == PaymentIntentStatus.APPROVED;
		boolean review = forceReview || attempt.status() == PaymentIntentStatus.REQUIRES_REVIEW;
		if (!applied && !review && attempt.status() == PaymentIntentStatus.PENDING) {
			OrderTransitionExecution confirmation = orderConfirmer.confirmWithinCurrentTransaction(
				attempt.orderId(), attempt.transitionIdempotencyKey(), "Mercado Pago QR");
			applied = !confirmation.expired();
			review = confirmation.expired();
		}
		else if (!applied && !review) {
			review = true;
		}
		VerifiedProviderPayment payment = new VerifiedProviderPayment(
			provider.paymentId(), credential.sellerAccountId(), null,
			provider.externalReference(), provider.paymentAmount(), provider.currencyCode(),
			credential.environment() == PaymentEnvironment.PRODUCTION,
			PaymentResultStatus.APPROVED, provider.updatedAt());
		paymentTransactions.applyVerifiedPayment(
			checkoutAttempt(attempt), payment, applied, review, clock.instant());
		return review ? QrOrderProcessingResult.REQUIRES_REVIEW
			: QrOrderProcessingResult.APPROVED;
	}

	private Prepared prepare(
			UUID orderId, byte[] tokenHash, UUID idempotencyKey,
			PaymentCredential credential, String externalPosId) {
		CheckoutOrder order = repository.lockOrder(orderId, tokenHash).orElseThrow(this::notFound);
		byte[] fingerprint = sha256("mercado-pago-qr:v1:" + orderId);
		Optional<StoredQrOrderAttempt> existingKey = repository.findByIdempotencyKey(idempotencyKey);
		if (existingKey.isPresent()) return replay(existingKey.get(), fingerprint, orderId);
		Optional<StoredQrOrderAttempt> current = repository.findCurrentByOrder(orderId, tokenHash);
		if (current.isPresent() && current.get().status() == PaymentIntentStatus.PENDING
				&& current.get().providerOrderId() != null
				&& current.get().providerExpiresAt().isAfter(clock.instant())) {
			return new Prepared(current.get(), true, false);
		}
		if (order.status() != OrderStatus.PENDING_CONFIRMATION
				|| !order.reservationExpiresAt().isAfter(clock.instant().plus(
					MINIMUM_PROVIDER_EXPIRATION).plus(EXPIRATION_SAFETY))) {
			throw invalid("ORDER_NOT_PAYABLE", "El pedido ya no está disponible para pagar.");
		}
		if (repository.hasBlockingIntent(order.internalId())) {
			throw invalid("PAYMENT_ALREADY_IN_PROGRESS", "El pedido ya tiene un pago en proceso.");
		}
		Instant now = clock.instant();
		Instant providerExpiresAt = order.reservationExpiresAt().minus(EXPIRATION_SAFETY);
		UUID attemptId = UUID.randomUUID();
		repository.insert(
			attemptId, order.internalId(), idempotencyKey, fingerprint, UUID.randomUUID(),
			repository.nextAttemptNumber(order.internalId()), order.amount(),
			order.currencyCode(), qrExternalReference(attemptId), UUID.randomUUID(),
			providerExpiresAt, credential.sellerAccountId(), credential.environment(),
			externalPosId, now);
		StoredQrOrderAttempt inserted = repository.findByPublicId(attemptId, false)
			.orElseThrow(this::notFound);
		return new Prepared(inserted, false, true);
	}

	private Prepared replay(UUID idempotencyKey, UUID orderId) {
		StoredQrOrderAttempt existing = repository.findByIdempotencyKey(idempotencyKey)
			.orElseThrow(this::notFound);
		return replay(existing, sha256("mercado-pago-qr:v1:" + orderId), orderId);
	}

	private Prepared replay(
			StoredQrOrderAttempt existing, byte[] fingerprint, UUID orderId) {
		if (!existing.orderId().equals(orderId)
				|| !MessageDigest.isEqual(existing.requestFingerprint(), fingerprint)) {
			throw invalid("QR_IDEMPOTENCY_CONFLICT",
				"Idempotency-Key ya fue usada para otra operación.");
		}
		if (existing.providerOrderId() != null) return new Prepared(existing, true, false);
		Instant now = clock.instant();
		boolean claimed = repository.claimCreation(
			existing, now, now.minus(CREATION_LEASE));
		if (!claimed) {
			throw new QrOrderException(
				"QR_CREATION_IN_PROGRESS", "La orden QR se está creando.", true, null);
		}
		StoredQrOrderAttempt claimedAttempt = repository.findByPublicId(existing.id(), false)
			.orElseThrow(this::notFound);
		return new Prepared(claimedAttempt, true, true);
	}

	private void validateProvider(
			StoredQrOrderAttempt attempt, PaymentCredential credential,
			ProviderQrOrder provider, boolean creation) {
		if (blank(provider.orderId()) || !"qr".equalsIgnoreCase(provider.type())
				|| blank(provider.status())
				|| !attempt.externalReference().equals(provider.externalReference())
				|| provider.totalAmount() == null
				|| attempt.amount().compareTo(provider.totalAmount()) != 0
				|| !attempt.currencyCode().equals(provider.currencyCode())
				|| blank(provider.sellerAccountId())
				|| !credential.sellerAccountId().equals(provider.sellerAccountId())
				|| (provider.liveMode() != null && provider.liveMode()
					!= (credential.environment() == PaymentEnvironment.PRODUCTION))
				|| (provider.externalPosId() != null
					&& !attempt.externalPosId().equals(provider.externalPosId()))
				|| (!creation && !attempt.providerOrderId().equals(provider.orderId()))
				|| (creation && blank(provider.qrData()))) {
			throw invalid("QR_PROVIDER_VALIDATION_FAILED",
				"La orden QR no coincide con el pago esperado.");
		}
	}

	private void validateAccreditedPayment(ProviderQrOrder provider) {
		String paymentStatus = provider.paymentStatus() == null
			? "" : provider.paymentStatus().toLowerCase(Locale.ROOT);
		if (blank(provider.paymentId())
				|| !(paymentStatus.equals("approved")
					|| paymentStatus.equals("accredited")
					|| paymentStatus.equals("processed"))
				|| provider.paymentAmount() == null
				|| provider.paymentAmount().compareTo(provider.totalAmount()) != 0) {
			throw invalid("QR_PAYMENT_NOT_ACCREDITED",
				"La transacción QR todavía no está acreditada.");
		}
	}

	private StoredCheckoutAttempt checkoutAttempt(StoredQrOrderAttempt attempt) {
		return new StoredCheckoutAttempt(
			attempt.internalId(), attempt.id(), attempt.orderInternalId(), attempt.orderId(),
			attempt.orderNumber(), attempt.orderStatus(), attempt.reservationExpiresAt(),
			attempt.idempotencyKey(), attempt.requestFingerprint(),
			attempt.transitionIdempotencyKey(), attempt.status(), attempt.amount(),
			attempt.currencyCode(), attempt.externalReference(), attempt.providerExpiresAt(),
			null, null, attempt.providerExpiresAt(), attempt.sellerAccountId(),
			attempt.environment(), attempt.updatedAt(), attempt.version());
	}

	private void ensureRoute(ResolvedTenant tenant, StoredQrOrderAttempt attempt) {
		controlTransactions.executeWithoutResult(status -> controlRepository.insertRoute(
			UUID.randomUUID(), tenant.id(), attempt.environment(), attempt.id(),
			attempt.providerOrderId(), attempt.sellerAccountId(),
			attempt.providerExpiresAt(), clock.instant()));
	}

	private StoredQrSetup readySetup(long tenantId) {
		return controlTransactions.execute(status -> setupRepository.find(tenantId, environment())
			.filter(setup -> setup.status() == QrProvisioningStatus.LISTO)
			.filter(setup -> setup.authorization() == QrAuthorizationStatus.AUTHORIZED)
			.filter(setup -> !blank(setup.providerStoreId()))
			.filter(setup -> !blank(setup.providerPosId()))
			.filter(setup -> !blank(setup.externalPosId()))
			.orElseThrow(() -> invalid(
				"QR_SETUP_NOT_READY", "El comercio todavía no configuró Mercado Pago QR.")));
	}

	private PaymentCredential productionCredential(ResolvedTenant tenant) {
		PaymentCredential credential = credentials.resolve(tenant.id(), tenant.slug());
		if (credential.environment() != PaymentEnvironment.PRODUCTION
				|| credential.source() != PaymentCredential.Source.TENANT_OAUTH) {
			throw invalid("QR_PRODUCTION_OAUTH_REQUIRED",
				"Mercado Pago QR requiere la conexión productiva del comercio.");
		}
		return credential;
	}

	private QrOrderInitiation initiation(StoredQrOrderAttempt attempt, boolean replayed) {
		if (attempt.status() != PaymentIntentStatus.EXPIRED
				&& attempt.providerOrderId() == null) {
			throw new QrOrderException(
				"QR_CREATION_IN_PROGRESS", "La orden QR se está creando.", true, null);
		}
		return new QrOrderInitiation(
			attempt.id(), attempt.status() == PaymentIntentStatus.EXPIRED ? null : attempt.qrData(),
			attempt.providerExpiresAt(), attempt.status(), replayed);
	}

	private Duration durationUntil(Instant expiresAt) {
		long seconds = Duration.between(clock.instant(), expiresAt).toSeconds();
		if (seconds < MINIMUM_PROVIDER_EXPIRATION.toSeconds()) {
			throw invalid("ORDER_NOT_PAYABLE", "La reserva está demasiado próxima a vencer.");
		}
		return Duration.ofSeconds(seconds);
	}

	private String qrExternalReference(UUID attemptId) {
		return "cf_qr_" + attemptId.toString().replace("-", "");
	}

	private void validateInput(UUID orderId, String token, UUID idempotencyKey) {
		validateOrderToken(orderId, token);
		if (idempotencyKey == null || idempotencyKey.version() != 4) {
			throw invalid("INVALID_IDEMPOTENCY_KEY",
				"Idempotency-Key debe ser un UUID v4 válido.");
		}
	}

	private void validateOrderToken(UUID orderId, String token) {
		if (orderId == null || orderId.version() != 4
				|| token == null || !token.matches("^[A-Za-z0-9_-]{43}$")) {
			throw notFound();
		}
	}

	private void requireEnabled() {
		if (!paymentProperties.enabled() || !oauthProperties.enabled()) {
			throw invalid("PAYMENTS_NOT_ENABLED", "Los pagos no están habilitados.");
		}
	}

	private PaymentEnvironment environment() {
		return oauthProperties.environment();
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

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private QrOrderException notFound() {
		return invalid("QR_ORDER_NOT_FOUND", "No se encontró la orden QR.");
	}

	private QrOrderException invalid(String code, String message) {
		return new QrOrderException(code, message);
	}

	private record Prepared(
		StoredQrOrderAttempt attempt,
		boolean replayed,
		boolean createAtProvider) {
	}
}
