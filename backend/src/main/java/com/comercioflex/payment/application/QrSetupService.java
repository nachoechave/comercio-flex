package com.comercioflex.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.payment.domain.PaymentEnvironment;

@Service
public class QrSetupService {

	private static final Duration VERIFYING_LEASE = Duration.ofMinutes(2);

	private final QrSetupRepository repository;
	private final MercadoPagoQrProvisioningGateway gateway;
	private final PaymentCredentialResolver credentials;
	private final PaymentOAuthProperties oauthProperties;
	private final TransactionTemplate transactions;
	private final Clock clock;

	@Autowired
	public QrSetupService(
			QrSetupRepository repository,
			MercadoPagoQrProvisioningGateway gateway,
			PaymentCredentialResolver credentials,
			PaymentOAuthProperties oauthProperties,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactions) {
		this(repository, gateway, credentials, oauthProperties, transactions,
			Clock.systemUTC());
	}

	QrSetupService(
			QrSetupRepository repository,
			MercadoPagoQrProvisioningGateway gateway,
			PaymentCredentialResolver credentials,
			PaymentOAuthProperties oauthProperties,
			TransactionTemplate transactions,
			Clock clock) {
		this.repository = repository;
		this.gateway = gateway;
		this.credentials = credentials;
		this.oauthProperties = oauthProperties;
		this.transactions = transactions;
		this.clock = clock;
	}

	public QrSetupView view(long tenantId, String tenantSlug) {
		return Objects.requireNonNull(transactions.execute(status -> {
			repository.requireActiveTenant(tenantId, tenantSlug);
			return repository.find(tenantId, environment())
				.map(this::view)
				.orElseGet(this::notConfigured);
		}));
	}

	public QrSetupView discover(long tenantId, String tenantSlug) {
		QrSetupTenant tenant = requireTenant(tenantId, tenantSlug);
		StoredQrSetup setup = ensureSetup(tenant);
		claim(setup);
		ProviderProgress progress = new ProviderProgress(
			setup.providerStoreId(), setup.providerPosId());
		try {
			PaymentCredential credential = productionCredential(tenantId, tenantSlug);
			Discovery discovery = discover(credential, setup, progress);
			progress.capture(discovery);
			return persist(setup, discovery, null);
		}
		catch (QrProviderException exception) {
			persistFailure(
				setup, progress, exception.category(), exception.category().name());
			throw new QrSetupException(
				"QR_PROVIDER_" + exception.category().name(), exception.getMessage(), exception);
		}
		catch (PaymentOAuthException exception) {
			persistFailure(
				setup, progress, QrAuthorizationStatus.NOT_FOUND, "OAUTH_NOT_AVAILABLE");
			throw exception;
		}
		catch (RuntimeException exception) {
			persistFailure(
				setup, progress, QrAuthorizationStatus.AUTHORIZED, "QR_VALIDATION_FAILED");
			throw exception;
		}
	}

	public QrSetupView configure(
			long tenantId,
			String tenantSlug,
			QrStoreSetupCommand command) {
		QrSetupTenant tenant = requireTenant(tenantId, tenantSlug);
		StoredQrSetup setup = ensureSetup(tenant);
		claim(setup);
		ProviderProgress progress = new ProviderProgress(
			setup.providerStoreId(), setup.providerPosId());
		try {
			PaymentCredential credential = productionCredential(tenantId, tenantSlug);
			Discovery existing = discover(credential, setup, progress);
			progress.capture(existing);
			QrProviderStore store = existing.store().orElseGet(() ->
				createOrRecoverStore(credential, setup, command));
			progress.providerStoreId = store.providerId();
			validateStore(setup, store);
			QrProviderPos pos = existing.pos().orElseGet(() ->
				createOrRecoverPos(credential, setup, store));
			progress.providerPosId = pos.providerId();
			validatePos(credential, setup, store, pos);
			return persist(setup, new Discovery(
				Optional.of(store), Optional.of(pos)), null);
		}
		catch (QrProviderException exception) {
			persistFailure(
				setup, progress, exception.category(), exception.category().name());
			throw new QrSetupException(
				"QR_PROVIDER_" + exception.category().name(), exception.getMessage(), exception);
		}
		catch (PaymentOAuthException exception) {
			persistFailure(
				setup, progress, QrAuthorizationStatus.NOT_FOUND, "OAUTH_NOT_AVAILABLE");
			throw exception;
		}
		catch (RuntimeException exception) {
			persistFailure(
				setup, progress, QrAuthorizationStatus.AUTHORIZED, "QR_VALIDATION_FAILED");
			throw exception;
		}
	}

	private QrSetupTenant requireTenant(long tenantId, String tenantSlug) {
		return Objects.requireNonNull(transactions.execute(status ->
			repository.requireActiveTenant(tenantId, tenantSlug)));
	}

	private StoredQrSetup ensureSetup(QrSetupTenant tenant) {
		String suffix = environment() == PaymentEnvironment.PRODUCTION ? "P" : "T";
		String opaqueTenantId = tenant.publicId().toString().replace("-", "");
		return Objects.requireNonNull(transactions.execute(status ->
			repository.createIfMissing(
				tenant.id(), environment(),
				"CFS" + suffix + opaqueTenantId,
				"CFP" + suffix + opaqueTenantId,
				UUID.randomUUID(), clock.instant())));
	}

	private void claim(StoredQrSetup setup) {
		Instant now = clock.instant();
		boolean claimed = Boolean.TRUE.equals(transactions.execute(status ->
			repository.claimVerification(
				setup, now, now.minus(VERIFYING_LEASE))));
		if (!claimed) {
			throw new QrSetupException(
				"QR_SETUP_IN_PROGRESS", "La configuración QR ya se está verificando.");
		}
	}

	private PaymentCredential productionCredential(long tenantId, String tenantSlug) {
		PaymentCredential credential = credentials.resolve(tenantId, tenantSlug);
		if (credential.environment() != PaymentEnvironment.PRODUCTION
				|| credential.source() != PaymentCredential.Source.TENANT_OAUTH) {
			throw new QrSetupException(
				"QR_PRODUCTION_OAUTH_REQUIRED",
				"Código QR requiere la conexión OAuth productiva del comercio.");
		}
		return credential;
	}

	private Discovery discover(
			PaymentCredential credential,
			StoredQrSetup setup,
			ProviderProgress progress) {
		Optional<QrProviderStore> store = gateway.findStore(
			credential, setup.externalStoreId());
		store.ifPresent(value -> progress.providerStoreId = value.providerId());
		Optional<QrProviderPos> pos = gateway.findPos(
			credential, setup.externalPosId());
		pos.ifPresent(value -> progress.providerPosId = value.providerId());
		store.ifPresent(value -> validateStore(setup, value));
		if (pos.isPresent()) {
			QrProviderStore linkedStore = store.orElseThrow(() -> invalid(
				"QR_POS_STORE_MISMATCH", "La caja QR no pertenece a la sucursal esperada."));
			validatePos(credential, setup, linkedStore, pos.get());
		}
		return new Discovery(store, pos);
	}

	private QrProviderStore createOrRecoverStore(
			PaymentCredential credential,
			StoredQrSetup setup,
			QrStoreSetupCommand command) {
		try {
			return gateway.createStore(credential, setup.externalStoreId(), command);
		}
		catch (QrProviderException exception) {
			if (!exception.recoverySearchAllowed()) {
				throw exception;
			}
			return gateway.findStore(credential, setup.externalStoreId())
				.orElseThrow(() -> exception);
		}
	}

	private QrProviderPos createOrRecoverPos(
			PaymentCredential credential,
			StoredQrSetup setup,
			QrProviderStore store) {
		try {
			return gateway.createPos(
				credential, store.providerId(), store.externalId(),
				setup.externalPosId(), setup.posIdempotencyKey());
		}
		catch (QrProviderException exception) {
			if (!exception.recoverySearchAllowed()) {
				throw exception;
			}
			return gateway.findPos(credential, setup.externalPosId())
				.orElseThrow(() -> exception);
		}
	}

	private void validateStore(StoredQrSetup setup, QrProviderStore store) {
		if (blank(store.providerId())
				|| !setup.externalStoreId().equals(store.externalId())) {
			throw invalid(
				"QR_STORE_MISMATCH", "La sucursal encontrada no coincide con esta tienda.");
		}
	}

	private void validatePos(
			PaymentCredential credential,
			StoredQrSetup setup,
			QrProviderStore store,
			QrProviderPos pos) {
		if (blank(pos.providerId())
				|| !setup.externalPosId().equals(pos.externalId())
				|| !store.providerId().equals(pos.providerStoreId())
				|| !store.externalId().equals(pos.externalStoreId())
				|| !credential.sellerAccountId().equals(pos.sellerAccountId())
				|| !"active".equalsIgnoreCase(pos.status())
				|| !"pdv".equalsIgnoreCase(pos.operatingMode())) {
			throw invalid(
				"QR_POS_MISMATCH", "La caja QR no coincide con el comercio conectado.");
		}
	}

	private QrSetupView persist(
			StoredQrSetup setup, Discovery discovery, String safeErrorCode) {
		boolean ready = discovery.store().isPresent() && discovery.pos().isPresent();
		QrProvisioningStatus status = ready
			? QrProvisioningStatus.LISTO : QrProvisioningStatus.NO_CONFIGURADO;
		transactions.executeWithoutResult(transaction -> repository.saveResult(
			setup,
			discovery.store().map(QrProviderStore::providerId).orElse(null),
			discovery.pos().map(QrProviderPos::providerId).orElse(null),
			status, QrAuthorizationStatus.AUTHORIZED, safeErrorCode, clock.instant()));
		return new QrSetupView(
			environment(), status, QrAuthorizationStatus.AUTHORIZED,
			discovery.store().isPresent(), discovery.pos().isPresent(), ready, ready);
	}

	private void persistFailure(
			StoredQrSetup setup,
			ProviderProgress progress,
			QrAuthorizationStatus authorization,
			String safeErrorCode) {
		transactions.executeWithoutResult(transaction -> repository.saveResult(
			setup, progress.providerStoreId, progress.providerPosId,
			QrProvisioningStatus.ERROR, authorization, safeErrorCode, clock.instant()));
	}

	private QrSetupView view(StoredQrSetup setup) {
		boolean store = !blank(setup.providerStoreId());
		boolean pos = !blank(setup.providerPosId());
		boolean ready = setup.status() == QrProvisioningStatus.LISTO
			&& setup.authorization() == QrAuthorizationStatus.AUTHORIZED
			&& store && pos && !blank(setup.externalPosId());
		return new QrSetupView(
			setup.environment(), setup.status(), setup.authorization(),
			store, pos, !blank(setup.externalPosId()) && pos, ready);
	}

	private QrSetupView notConfigured() {
		return new QrSetupView(
			environment(), QrProvisioningStatus.NO_CONFIGURADO,
			QrAuthorizationStatus.NOT_CHECKED, false, false, false, false);
	}

	private PaymentEnvironment environment() {
		return oauthProperties.environment();
	}

	private QrSetupException invalid(String code, String message) {
		return new QrSetupException(code, message);
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private record Discovery(
		Optional<QrProviderStore> store,
		Optional<QrProviderPos> pos) {
	}

	private static final class ProviderProgress {

		private String providerStoreId;
		private String providerPosId;

		private ProviderProgress(String providerStoreId, String providerPosId) {
			this.providerStoreId = providerStoreId;
			this.providerPosId = providerPosId;
		}

		private void capture(Discovery discovery) {
			discovery.store().ifPresent(store -> providerStoreId = store.providerId());
			discovery.pos().ifPresent(pos -> providerPosId = pos.providerId());
		}
	}
}
