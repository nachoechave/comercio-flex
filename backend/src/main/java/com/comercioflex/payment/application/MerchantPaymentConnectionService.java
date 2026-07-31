package com.comercioflex.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.payment.domain.MerchantConnectionStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

@Service
public class MerchantPaymentConnectionService {

	private static final String PROVIDER = "MERCADO_PAGO";
	private static final Set<String> REQUIRED_SCOPES = Set.of("read", "write", "offline_access");
	private static final Duration ATTEMPT_LIFETIME = Duration.ofMinutes(10);
	private static final Duration REFRESH_WINDOW = Duration.ofMinutes(5);

	private final MerchantOAuthRepository repository;
	private final MerchantOAuthClient client;
	private final CredentialCipher cipher;
	private final PaymentOAuthProperties properties;
	private final TransactionTemplate transactions;
	private final SecureRandom random;
	private final Clock clock;

	@Autowired
	public MerchantPaymentConnectionService(
			MerchantOAuthRepository repository,
			MerchantOAuthClient client,
			CredentialCipher cipher,
			PaymentOAuthProperties properties,
			@Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
		this(repository, client, cipher, properties, new TransactionTemplate(transactionManager),
			new SecureRandom(), Clock.systemUTC());
	}

	MerchantPaymentConnectionService(
			MerchantOAuthRepository repository,
			MerchantOAuthClient client,
			CredentialCipher cipher,
			PaymentOAuthProperties properties,
			TransactionTemplate transactions,
			SecureRandom random,
			Clock clock) {
		this.repository = repository;
		this.client = client;
		this.cipher = cipher;
		this.properties = properties;
		this.transactions = transactions;
		this.random = random;
		this.clock = clock;
	}

	public PaymentAuthorizationStart start(
			long tenantId,
			String tenantSlug,
			PlatformPrincipal principal) {
		requireEnabled();
		Instant now = clock.instant();
		String state = randomValue(32);
		String verifier = randomValue(64);
		String challenge = base64Url(sha256(verifier));
		UUID attemptId = UUID.randomUUID();
		Instant expiresAt = now.plus(ATTEMPT_LIFETIME);
		try {
			transactions.executeWithoutResult(status -> {
				OAuthTenantIdentity tenant = repository.requireActiveTenant(tenantId, tenantSlug);
				EncryptedSecret encryptedVerifier = cipher.encrypt(
					verifier, context(tenant.publicId(), attemptId, "pkce_verifier"));
				repository.supersedePending(tenantId, principal.id(), environment(), now);
				repository.insertAttempt(
					attemptId, tenantId, principal.id(), principal.publicId(), environment(),
					sha256(state), encryptedVerifier, expiresAt);
			});
		}
		catch (DataIntegrityViolationException exception) {
			throw new PaymentOAuthException(
				"OAUTH_ALREADY_IN_PROGRESS",
				"Ya hay una autorización de Mercado Pago en proceso.", exception);
		}
		return new PaymentAuthorizationStart(client.authorizationUri(state, challenge), expiresAt);
	}

	public PaymentConnectionView view(long tenantId, String tenantSlug) {
		requireEnabled();
		Instant now = clock.instant();
		return Objects.requireNonNull(transactions.execute(status -> {
			repository.requireActiveTenant(tenantId, tenantSlug);
			Optional<StoredMerchantConnection> stored =
				repository.findConnection(tenantId, environment(), false);
			if (stored.isPresent()
					&& stored.get().status() != MerchantConnectionStatus.DISCONNECTED) {
				StoredMerchantConnection connection = stored.get();
				return new PaymentConnectionView(
					PROVIDER, environment(), connection.status().name(),
					accountLabel(connection), connection.connectedAt());
			}
			String state = repository.hasPendingAttempt(tenantId, environment(), now)
				? "AUTHORIZATION_PENDING" : "NOT_CONNECTED";
			return new PaymentConnectionView(PROVIDER, environment(), state, null, null);
		}));
	}

	public OAuthCallbackResult complete(
			String state,
			String code,
			String providerError,
			PlatformPrincipal principal) {
		requireEnabled();
		if (state == null || state.isBlank()) {
			throw oauth("INVALID_STATE", "La autorización no es válida o venció.");
		}
		Instant now = clock.instant();
		ClaimedOAuthAttempt attempt = transactions.execute(status -> repository.claimAttempt(
			sha256(state), principal.id(), environment(), now)
			.orElseThrow(() -> oauth(
				"INVALID_STATE", "La autorización no es válida o ya fue procesada.")));
		Objects.requireNonNull(attempt);
		if (providerError != null && !providerError.isBlank()) {
			transactions.executeWithoutResult(status -> repository.markAttemptFailed(
				attempt.internalId(), "CONSENT_DENIED", clock.instant()));
			return new OAuthCallbackResult(attempt.tenantSlug(), "cancelled");
		}
		try {
			if (code == null || code.isBlank()) {
				throw oauth("MISSING_AUTHORIZATION_CODE", "Mercado Pago no devolvió autorización.");
			}
			String verifier = cipher.decrypt(
				attempt.pkceVerifier(),
				context(attempt.tenantPublicId(), attempt.publicId(), "pkce_verifier"));
			OAuthTokenResponse token = client.exchange(code, verifier);
			ValidatedIdentity identity = validateIdentity(token);
			transactions.executeWithoutResult(status -> connect(attempt, token, identity, now));
			return new OAuthCallbackResult(attempt.tenantSlug(), "connected");
		}
		catch (RuntimeException exception) {
			String failureCode = exception instanceof PaymentOAuthException oauth
				? oauth.code() : "OAUTH_COMPLETION_FAILED";
			transactions.executeWithoutResult(status ->
				repository.markAttemptFailed(attempt.internalId(), failureCode, clock.instant()));
			throw new PaymentOAuthCallbackException(
				attempt.tenantSlug(), failureCode,
				"No se pudo completar la autorización con Mercado Pago.", exception);
		}
	}

	public void disconnect(
			long tenantId,
			String tenantSlug,
			PlatformPrincipal principal) {
		requireEnabled();
		transactions.executeWithoutResult(status -> {
			repository.requireActiveTenant(tenantId, tenantSlug);
			repository.findConnection(tenantId, environment(), true)
				.filter(connection -> connection.status() != MerchantConnectionStatus.DISCONNECTED)
				.ifPresent(connection -> repository.disconnect(
					connection, principal.id(), principal.publicId(), clock.instant()));
		});
	}

	/** Returns a usable token to PAY-01C, refreshing both rotating tokens atomically. */
	public String requireActiveAccessToken(long tenantId) {
		requireEnabled();
		try {
			return Objects.requireNonNull(transactions.execute(status -> {
				StoredMerchantConnection connection = repository
					.findConnection(tenantId, environment(), true)
					.filter(item -> item.status() == MerchantConnectionStatus.CONNECTED)
					.orElseThrow(() -> oauth(
						"PAYMENT_ACCOUNT_NOT_CONNECTED", "La cuenta de cobros no está conectada."));
				if (connection.accessTokenExpiresAt().isAfter(clock.instant().plus(REFRESH_WINDOW))) {
					return decrypt(connection, connection.accessToken(), "access_token");
				}
				OAuthTokenResponse token = client.refresh(
					decrypt(connection, connection.refreshToken(), "refresh_token"));
				ValidatedIdentity identity = validateIdentity(token);
				if (!connection.sellerAccountId().equals(identity.accountId())) {
					throw oauth("SELLER_IDENTITY_CHANGED", "La identidad vendedora cambió.");
				}
				EncryptedSecret access = cipher.encrypt(token.accessToken(),
					context(connection.tenantPublicId(), connection.publicId(), "access_token"));
				EncryptedSecret refresh = cipher.encrypt(token.refreshToken(),
					context(connection.tenantPublicId(), connection.publicId(), "refresh_token"));
				repository.replaceRefreshedTokens(
					connection, token.scopes(), access, refresh,
					clock.instant().plus(token.expiresIn()), clock.instant());
				return token.accessToken();
			}));
		}
		catch (PaymentOAuthException exception) {
			if (Set.of("REFRESH_REJECTED", "SELLER_IDENTITY_CHANGED")
					.contains(exception.code())) {
				transactions.executeWithoutResult(status -> repository
					.findConnection(tenantId, environment(), true)
					.filter(item -> item.status() == MerchantConnectionStatus.CONNECTED)
					.ifPresent(connection -> repository.requireReauthorization(
						connection, exception.code(), clock.instant())));
			}
			throw exception;
		}
	}

	private void connect(
			ClaimedOAuthAttempt attempt,
			OAuthTokenResponse token,
			ValidatedIdentity identity,
			Instant now) {
		Optional<StoredMerchantConnection> existing =
			repository.findConnection(attempt.tenantId(), environment(), true);
		if (existing.isPresent()
				&& existing.get().status() != MerchantConnectionStatus.DISCONNECTED
				&& !existing.get().sellerAccountId().equals(identity.accountId())) {
			throw oauth("DISCONNECT_REQUIRED", "Desconectá la cuenta actual antes de cambiarla.");
		}
		repository.findActiveBySeller(identity.accountId(), environment(), true)
			.filter(connection -> connection.tenantId() != attempt.tenantId())
			.ifPresent(connection -> {
				throw oauth("SELLER_ALREADY_CONNECTED", "La cuenta ya está conectada a otro comercio.");
			});
		UUID connectionId = existing.map(StoredMerchantConnection::publicId)
			.orElseGet(UUID::randomUUID);
		EncryptedSecret access = cipher.encrypt(token.accessToken(),
			context(attempt.tenantPublicId(), connectionId, "access_token"));
		EncryptedSecret refresh = cipher.encrypt(token.refreshToken(),
			context(attempt.tenantPublicId(), connectionId, "refresh_token"));
		try {
			repository.upsertConnected(
				connectionId, attempt.tenantId(), environment(), identity.accountId(),
				identity.nickname(), token.scopes(), access, refresh,
				now.plus(token.expiresIn()), attempt.initiatedByUserId(),
				attempt.initiatedByUserPublicId(), attempt.publicId(), now, existing);
			repository.markAttemptSucceeded(attempt.internalId(), now);
		}
		catch (DataIntegrityViolationException exception) {
			throw new PaymentOAuthException(
				"SELLER_ALREADY_CONNECTED", "La cuenta ya está conectada a otro comercio.", exception);
		}
	}

	private ValidatedIdentity validateIdentity(OAuthTokenResponse token) {
		if (token == null || blank(token.accessToken()) || blank(token.refreshToken())
				|| blank(token.sellerAccountId()) || token.expiresIn() == null
				|| token.expiresIn().isNegative() || token.expiresIn().isZero()
				|| token.tokenType() == null || !"bearer".equalsIgnoreCase(token.tokenType())
				|| token.scopes() == null || !token.scopes().containsAll(REQUIRED_SCOPES)
				|| token.liveMode() != (environment() == PaymentEnvironment.PRODUCTION)) {
			throw oauth("INVALID_PROVIDER_RESPONSE", "Mercado Pago devolvió credenciales no válidas.");
		}
		SellerAccountProfile profile = client.fetchSellerProfile(token.accessToken());
		if (profile == null || !token.sellerAccountId().equals(profile.id())) {
			throw oauth("SELLER_IDENTITY_MISMATCH", "No se pudo verificar la cuenta vendedora.");
		}
		String nickname = sanitizeNickname(profile.nickname());
		return new ValidatedIdentity(token.sellerAccountId(), nickname);
	}

	private String sanitizeNickname(String value) {
		if (blank(value)) {
			throw oauth("SELLER_NICKNAME_MISSING", "La cuenta no tiene un usuario público verificable.");
		}
		String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
		if (cleaned.isBlank()) {
			throw oauth("SELLER_NICKNAME_MISSING", "La cuenta no tiene un usuario público verificable.");
		}
		return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
	}

	private String accountLabel(StoredMerchantConnection connection) {
		if (!blank(connection.sellerNickname())) {
			return connection.sellerNickname();
		}
		String id = connection.sellerAccountId();
		return id.length() <= 4 ? id : "••••" + id.substring(id.length() - 4);
	}

	private String decrypt(
			StoredMerchantConnection connection,
			EncryptedSecret secret,
			String field) {
		return cipher.decrypt(secret,
			context(connection.tenantPublicId(), connection.publicId(), field));
	}

	private EncryptionContext context(UUID tenantId, UUID subjectId, String field) {
		return new EncryptionContext(
			tenantId.toString(), PROVIDER, environment().name(), subjectId.toString(), field);
	}

	private void requireEnabled() {
		if (!properties.enabled()) {
			throw oauth("PAYMENT_CONNECTION_DISABLED", "La conexión con Mercado Pago no está habilitada.");
		}
		if (blank(properties.clientId()) || blank(properties.clientSecret())
				|| properties.redirectUri() == null) {
			throw oauth("PAYMENT_CONFIGURATION_INVALID", "La integración de pagos está incompleta.");
		}
	}

	private PaymentEnvironment environment() {
		return client.environment();
	}

	private String randomValue(int bytes) {
		byte[] value = new byte[bytes];
		random.nextBytes(value);
		return base64Url(value);
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}

	private String base64Url(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private PaymentOAuthException oauth(String code, String message) {
		return new PaymentOAuthException(code, message);
	}

	private record ValidatedIdentity(String accountId, String nickname) {
	}
}
