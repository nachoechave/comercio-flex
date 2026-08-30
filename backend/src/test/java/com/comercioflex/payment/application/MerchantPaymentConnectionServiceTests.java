package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.UserCredentials;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.payment.domain.MerchantConnectionStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class MerchantPaymentConnectionServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
	private static final UUID TENANT_PUBLIC_ID =
		UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID USER_PUBLIC_ID =
		UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	private final MerchantOAuthRepository repository = mock(MerchantOAuthRepository.class);
	private final MerchantOAuthClient client = mock(MerchantOAuthClient.class);
	private final TransactionTemplate transactions = mock(TransactionTemplate.class);
	private final CredentialCipher cipher = new PlainTestCipher();
	private MerchantPaymentConnectionService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(client.environment()).thenReturn(PaymentEnvironment.TEST);
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactions).executeWithoutResult(any());
		service = new MerchantPaymentConnectionService(
			repository,
			client,
			cipher,
			properties(),
			transactions,
			new SecureRandom(new byte[] {1, 2, 3}),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void exposesOnlyTheVerifiedPublicNicknameAsAccountLabel() {
		when(repository.requireActiveTenant(1L, "tienda-a"))
			.thenReturn(new OAuthTenantIdentity(1L, TENANT_PUBLIC_ID, "tienda-a"));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, false))
			.thenReturn(Optional.of(connection(
				MerchantConnectionStatus.CONNECTED, "123456789", "CARNES_DEL_SUR")));

		PaymentConnectionView view = service.view(1L, "tienda-a");

		assertThat(view.connectedAccountLabel()).isEqualTo("CARNES_DEL_SUR");
		assertThat(view.toString()).doesNotContain("access-token", "refresh-token");
	}

	@Test
	void startsAShortLivedOneUseStateWithPkceS256() {
		when(repository.requireActiveTenant(1L, "tienda-a"))
			.thenReturn(new OAuthTenantIdentity(1L, TENANT_PUBLIC_ID, "tienda-a"));
		ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> challenge = ArgumentCaptor.forClass(String.class);
		when(client.authorizationUri(state.capture(), challenge.capture()))
			.thenReturn(URI.create("https://auth.mercadopago.com/authorization"));

		PaymentAuthorizationStart result = service.start(1L, "tienda-a", principal());

		ArgumentCaptor<byte[]> stateHash = ArgumentCaptor.forClass(byte[].class);
		ArgumentCaptor<EncryptedSecret> verifier = ArgumentCaptor.forClass(EncryptedSecret.class);
		verify(repository).insertAttempt(
			any(), eq(1L), eq(7L), eq(USER_PUBLIC_ID), eq(PaymentEnvironment.TEST),
			stateHash.capture(), verifier.capture(), eq(NOW.plusSeconds(600)));
		assertThat(state.getValue()).isNotBlank().doesNotContain("=");
		assertThat(challenge.getValue()).hasSize(43).doesNotContain("=");
		assertThat(stateHash.getValue()).hasSize(32);
		assertThat(new String(verifier.getValue().ciphertext(), StandardCharsets.UTF_8))
			.isNotEqualTo(challenge.getValue());
		assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(600));
	}

	@Test
	void createsANewUnpredictableStateForEveryAuthorizationAttempt() {
		when(repository.requireActiveTenant(1L, "tienda-a"))
			.thenReturn(new OAuthTenantIdentity(1L, TENANT_PUBLIC_ID, "tienda-a"));
		ArgumentCaptor<String> states = ArgumentCaptor.forClass(String.class);
		when(client.authorizationUri(states.capture(), anyString()))
			.thenReturn(URI.create("https://auth.mercadopago.com/authorization"));

		service.start(1L, "tienda-a", principal());
		service.start(1L, "tienda-a", principal());

		assertThat(states.getAllValues()).hasSize(2).doesNotHaveDuplicates();
	}

	@Test
	void rejectsAnInvalidOrAlreadyConsumedStateBeforeTokenExchange() {
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.complete("unknown-state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthException.class)
			.extracting(exception -> ((PaymentOAuthException) exception).code())
			.isEqualTo("INVALID_STATE");
		verify(client, never()).exchange(anyString(), anyString());
	}

	@Test
	void consumesAndAuditsAProviderCancellationWithoutExchangingTokens() {
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt()));

		OAuthCallbackResult result = service.complete(
			"state", null, "access_denied", principal());

		assertThat(result).isEqualTo(new OAuthCallbackResult("tienda-a", "cancelled"));
		verify(repository).markAttemptFailed(50L, "CONSENT_DENIED", NOW);
		verify(client, never()).exchange(anyString(), anyString());
	}

	@Test
	void rejectsAProfileWhoseCanonicalIdDoesNotMatchTheOAuthUserId() {
		ClaimedOAuthAttempt attempt = attempt();
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt));
		when(client.exchange("code", "verifier")).thenReturn(token("123456789"));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("999999999", "OTRA_CUENTA"));

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("SELLER_IDENTITY_MISMATCH");
		verify(repository).markAttemptFailed(50L, "SELLER_IDENTITY_MISMATCH", NOW);
	}

	@Test
	void storesTheVerifiedNicknameAndBothEncryptedTokens() {
		ClaimedOAuthAttempt attempt = attempt();
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("123456789", PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(client.exchange("code", "verifier")).thenReturn(token("123456789"));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("123456789", "  CARNES_DEL_SUR  "));

		OAuthCallbackResult result = service.complete("state", "code", null, principal());

		assertThat(result).isEqualTo(new OAuthCallbackResult("tienda-a", "connected"));
		ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
		verify(repository).upsertConnected(
			any(), eq(1L), eq(PaymentEnvironment.TEST), eq("123456789"),
			nickname.capture(), eq(Set.of("read", "write", "offline_access")),
			any(), any(), eq(NOW.plusSeconds(3600)), eq(7L), eq(USER_PUBLIC_ID),
			eq(attempt.publicId()), eq(NOW), eq(Optional.empty()), any(Runnable.class));
		assertThat(nickname.getValue()).isEqualTo("CARNES_DEL_SUR");
		verify(repository).markAttemptSucceeded(50L, NOW);
	}

	@Test
	void connectsTheFirstProductionSellerWhenNoConnectionExists() {
		when(client.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
		ClaimedOAuthAttempt attempt = attempt(PaymentEnvironment.PRODUCTION);
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.PRODUCTION), eq(NOW)))
			.thenReturn(Optional.of(attempt));
		when(repository.findConnection(1L, PaymentEnvironment.PRODUCTION, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.PRODUCTION, true))
			.thenReturn(Optional.empty());
		when(client.exchange("code", "verifier")).thenReturn(token("seller-a", true));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("seller-a", "SELLER_A"));

		OAuthCallbackResult result = service.complete("state", "code", null, principal());

		assertThat(result).isEqualTo(new OAuthCallbackResult("tienda-a", "connected"));
		verify(repository).upsertConnected(
			any(), eq(1L), eq(PaymentEnvironment.PRODUCTION), eq("seller-a"), eq("SELLER_A"),
			eq(Set.of("read", "write", "offline_access")), any(), any(),
			eq(NOW.plusSeconds(3600)), eq(7L), eq(USER_PUBLIC_ID), eq(attempt.publicId()),
			eq(NOW), eq(Optional.empty()), any(Runnable.class));
		verify(repository).markAttemptSucceeded(50L, NOW);
	}

	@Test
	void rejectsASellerThatIsAlreadyConnectedToAnotherTenant() {
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt()));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, true))
			.thenReturn(Optional.of(connectionForTenant(2L, "seller-a")));
		when(client.exchange("code", "verifier")).thenReturn(token("seller-a"));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("seller-a", "SELLER_A"));

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("SELLER_ALREADY_CONNECTED");
		verify(repository).markAttemptFailed(50L, "SELLER_ALREADY_CONNECTED", NOW);
	}

	@Test
	void mapsAConcurrentInsertConflictOnlyWhenTheSellerNowBelongsToAnotherTenant(
			CapturedOutput output) {
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt()));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, false))
			.thenReturn(Optional.of(connectionForTenant(2L, "seller-a")));
		when(client.exchange("code", "verifier")).thenReturn(token("seller-a"));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("seller-a", "SELLER_A"));
		doThrow(new DataIntegrityViolationException("concurrent seller conflict"))
			.when(repository).upsertConnected(
				any(), any(Long.class), any(), anyString(), anyString(), any(), any(), any(),
				any(), any(Long.class), any(), any(), any(), any(), any(Runnable.class));

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("SELLER_ALREADY_CONNECTED");
		verify(repository).markAttemptFailed(50L, "SELLER_ALREADY_CONNECTED", NOW);
		assertThat(output).doesNotContain("payment_oauth_completion_integrity_failure");
	}

	@Test
	void logsConnectionStageWithoutLeakingJdbcValuesForAnUnrelatedIntegrityFailure(
			CapturedOutput output) {
		String accessToken = "access-secret-fixture";
		String refreshToken = "refresh-secret-fixture";
		String additionalScope = "scope-secret-fixture";
		Set<String> scopes = Set.of(
			"read", "write", "offline_access", additionalScope);
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt()));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-123", PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-123", PaymentEnvironment.TEST, false))
			.thenReturn(Optional.empty());
		when(client.exchange("code", "verifier")).thenReturn(new OAuthTokenResponse(
			accessToken, refreshToken, "Bearer", Duration.ofHours(1), scopes,
			"seller-123", false));
		when(client.fetchSellerProfile(accessToken))
			.thenReturn(new SellerAccountProfile("seller-123", "NICKNAME_SECRET"));
		SQLIntegrityConstraintViolationException sqlException =
			new SQLIntegrityConstraintViolationException(
				"Duplicate entry 'sensitive-fixture-value' for key 'ck_unrelated_constraint'",
				"23000", 3819);
		doThrow(new DataIntegrityViolationException("unrelated constraint", sqlException))
			.when(repository).upsertConnected(
				any(), any(Long.class), any(), anyString(), anyString(), any(), any(), any(),
				any(), any(Long.class), any(), any(), any(), any(), any(Runnable.class));

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("OAUTH_COMPLETION_FAILED");
		verify(repository).markAttemptFailed(50L, "OAUTH_COMPLETION_FAILED", NOW);
		assertThat(output)
			.contains("event=payment_oauth_completion_integrity_failure")
			.contains("tenant=" + TENANT_PUBLIC_ID)
			.contains("environment=TEST")
			.contains("stage=CONNECTION_UPSERT")
			.contains("sqlState=23000")
			.contains("vendorCode=3819")
			.contains("constraint=UNKNOWN")
			.contains("rootException=java.sql.SQLIntegrityConstraintViolationException")
			.contains("provider=12/30")
			.contains("environment=4/20")
			.contains("status=9/40")
			.contains("sellerAccountId=10/100")
			.contains("sellerNickname=15/120")
			.contains("grantedScopes="
				+ bytes(String.join(" ", scopes.stream().sorted().toList())).length
				+ "/65535")
			.contains("accessTokenCiphertext=" + bytes(accessToken).length + "/4096")
			.contains("refreshTokenCiphertext=" + bytes(refreshToken).length + "/4096")
			.contains("accessTokenNonce=12/12")
			.contains("refreshTokenNonce=12/12")
			.contains("accessTokenKeyId=5/64")
			.contains("refreshTokenKeyId=5/64")
			.contains("connectedByRole=5/30")
			.contains("overflowFields=NONE")
			.doesNotContain(
				"sensitive-fixture-value", "state", "code", "verifier",
				accessToken, refreshToken, additionalScope,
				"client-secret", "unused-by-test-cipher");
	}

	@Test
	void reportsOnlyOverflowFieldNamesWithoutLeakingOversizedContents(
			CapturedOutput output) {
		String oversizedScope = "scope-sensitive-" + "s".repeat(65_536);
		String oversizedAccessToken = "ciphertext-sensitive-" + "a".repeat(4096);
		Set<String> scopes = Set.of(
			"read", "write", "offline_access", oversizedScope);
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt()));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, false))
			.thenReturn(Optional.empty());
		when(client.exchange("code", "verifier")).thenReturn(new OAuthTokenResponse(
			oversizedAccessToken, "safe-refresh", "Bearer", Duration.ofHours(1),
			scopes, "seller-a", false));
		when(client.fetchSellerProfile(oversizedAccessToken))
			.thenReturn(new SellerAccountProfile("seller-a", "SELLER_A"));
		doThrow(integrityFailure("synthetic overflow", "22001", 1406))
			.when(repository).upsertConnected(
				any(), any(Long.class), any(), anyString(), anyString(), any(), any(), any(),
				any(), any(Long.class), any(), any(), any(), any(), any(Runnable.class));

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("OAUTH_COMPLETION_FAILED");
		assertThat(output)
			.contains("sqlState=22001")
			.contains("vendorCode=1406")
			.contains("grantedScopes="
				+ bytes(String.join(" ", scopes.stream().sorted().toList())).length
				+ "/65535")
			.contains("accessTokenCiphertext="
				+ bytes(oversizedAccessToken).length + "/4096")
			.contains("overflowFields=granted_scopes,access_token_ciphertext")
			.doesNotContain(
				oversizedScope, oversizedAccessToken, "safe-refresh", "synthetic overflow");
	}

	@Test
	void logsConnectionEventStageForAnUnexpectedIntegrityFailure(CapturedOutput output) {
		prepareCompletableSeller();
		DataIntegrityViolationException failure = integrityFailure(
			"Cannot add child row for CONSTRAINT `fk_event_actor`", "23000", 1452);
		doAnswer(invocation -> {
			Runnable beforeConnectionEvent = invocation.getArgument(14);
			beforeConnectionEvent.run();
			throw failure;
		}).when(repository).upsertConnected(
			any(), any(Long.class), any(), anyString(), anyString(), any(), any(), any(),
			any(), any(Long.class), any(), any(), any(), any(), any(Runnable.class));

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("OAUTH_COMPLETION_FAILED");
		assertThat(output)
			.contains("stage=CONNECTION_EVENT")
			.contains("constraint=UNKNOWN")
			.doesNotContain("fk_event_actor")
			.doesNotContain("Cannot add child row");
	}

	@Test
	void logsAttemptSuccessStageForAnUnexpectedIntegrityFailure(CapturedOutput output) {
		prepareCompletableSeller();
		doThrow(integrityFailure(
			"Check constraint 'ck_attempt_success' is violated", "23000", 3819))
			.when(repository).markAttemptSucceeded(50L, NOW);

		assertThatThrownBy(() -> service.complete("state", "code", null, principal()))
			.isInstanceOf(PaymentOAuthCallbackException.class)
			.extracting(exception -> ((PaymentOAuthCallbackException) exception).code())
			.isEqualTo("OAUTH_COMPLETION_FAILED");
		assertThat(output)
			.contains("stage=ATTEMPT_SUCCESS")
			.contains("constraint=UNKNOWN")
			.doesNotContain("ck_attempt_success")
			.doesNotContain("is violated");
	}

	@Test
	void persistsReauthorizationInASeparateTransactionAfterARejectedRefresh() {
		StoredMerchantConnection expiring = new StoredMerchantConnection(
			10L,
			UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
			1L,
			TENANT_PUBLIC_ID,
			PaymentEnvironment.TEST,
			MerchantConnectionStatus.CONNECTED,
			"123456789",
			"CARNES_DEL_SUR",
			new EncryptedSecret("plain", new byte[] {1}, bytes("access-token")),
			new EncryptedSecret("plain", new byte[] {1}, bytes("refresh-token")),
			NOW.plusSeconds(30),
			NOW.minusSeconds(60),
			1L);
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.of(expiring), Optional.of(expiring));
		when(client.refresh("refresh-token")).thenThrow(new PaymentOAuthException(
			"REFRESH_REJECTED", "Se requiere autorización."));

		assertThatThrownBy(() -> service.requireActiveAccessToken(1L))
			.isInstanceOf(PaymentOAuthException.class)
			.extracting(exception -> ((PaymentOAuthException) exception).code())
			.isEqualTo("REFRESH_REJECTED");
		verify(repository).requireReauthorization(expiring, "REFRESH_REJECTED", NOW);
	}

	@Test
	void returnsAValidTenantTokenWithoutRefreshingIt() {
		StoredMerchantConnection active = connection(
			MerchantConnectionStatus.CONNECTED, "123456789", "CARNES_DEL_SUR");
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.of(active));

		PaymentCredential credential = service.requireActiveCredential(1L);

		assertThat(credential.accessToken()).isEqualTo("access-token");
		assertThat(credential.sellerAccountId()).isEqualTo("123456789");
		assertThat(credential.source()).isEqualTo(PaymentCredential.Source.TENANT_OAUTH);
		verify(client, never()).refresh(anyString());
	}

	@Test
	void atomicallyPersistsBothRotatedTokensAfterRefresh() {
		StoredMerchantConnection expiring = new StoredMerchantConnection(
			10L,
			UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
			1L,
			TENANT_PUBLIC_ID,
			PaymentEnvironment.TEST,
			MerchantConnectionStatus.CONNECTED,
			"123456789",
			"CARNES_DEL_SUR",
			new EncryptedSecret("plain", new byte[] {1}, bytes("old-access")),
			new EncryptedSecret("plain", new byte[] {1}, bytes("old-refresh")),
			NOW.plusSeconds(30),
			NOW.minusSeconds(60),
			1L);
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.of(expiring));
		when(client.refresh("old-refresh")).thenReturn(token("123456789"));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("123456789", "CARNES_DEL_SUR"));

		PaymentCredential credential = service.requireActiveCredential(1L);

		assertThat(credential.accessToken()).isEqualTo("access-token");
		ArgumentCaptor<EncryptedSecret> access = ArgumentCaptor.forClass(EncryptedSecret.class);
		ArgumentCaptor<EncryptedSecret> refresh = ArgumentCaptor.forClass(EncryptedSecret.class);
		verify(repository).replaceRefreshedTokens(
			eq(expiring), eq(Set.of("read", "write", "offline_access")),
			access.capture(), refresh.capture(), eq(NOW.plusSeconds(3600)), eq(NOW));
		assertThat(new String(access.getValue().ciphertext(), StandardCharsets.UTF_8))
			.isEqualTo("access-token");
		assertThat(new String(refresh.getValue().ciphertext(), StandardCharsets.UTF_8))
			.isEqualTo("refresh-token");
	}

	@Test
	void reportsAvailabilityOnlyForAConnectedTenant() {
		when(repository.findConnection(1L, PaymentEnvironment.TEST, false))
			.thenReturn(Optional.of(connection(
				MerchantConnectionStatus.CONNECTED, "seller-a", "SELLER_A")));
		when(repository.findConnection(2L, PaymentEnvironment.TEST, false))
			.thenReturn(Optional.of(connection(
				MerchantConnectionStatus.REAUTHORIZATION_REQUIRED, "seller-b", "SELLER_B")));

		assertThat(service.isConnected(1L)).isTrue();
		assertThat(service.isConnected(2L)).isFalse();
	}

	private PaymentOAuthProperties properties() {
		return new PaymentOAuthProperties(
			true,
			PaymentEnvironment.TEST,
			"client-id",
			"client-secret",
			URI.create("https://api.example.test/oauth/callback"),
			URI.create("https://auth.mercadopago.com"),
			URI.create("https://api.mercadopago.com"),
			URI.create("https://api.mercadolibre.com"),
			URI.create("https://app.example.test"),
			Duration.ofSeconds(3),
			Duration.ofSeconds(8),
			"v1",
			"unused-by-test-cipher");
	}

	private PlatformPrincipal principal() {
		return new PlatformPrincipal(new UserCredentials(
			7L, USER_PUBLIC_ID, "owner@example.test", "Owner", "hash", UserStatus.ACTIVE));
	}

	private ClaimedOAuthAttempt attempt() {
		return attempt(PaymentEnvironment.TEST);
	}

	private void prepareCompletableSeller() {
		when(repository.claimAttempt(any(), eq(7L), eq(PaymentEnvironment.TEST), eq(NOW)))
			.thenReturn(Optional.of(attempt()));
		when(repository.findConnection(1L, PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, true))
			.thenReturn(Optional.empty());
		when(repository.findActiveBySeller("seller-a", PaymentEnvironment.TEST, false))
			.thenReturn(Optional.empty());
		when(client.exchange("code", "verifier")).thenReturn(token("seller-a"));
		when(client.fetchSellerProfile("access-token"))
			.thenReturn(new SellerAccountProfile("seller-a", "SELLER_A"));
	}

	private DataIntegrityViolationException integrityFailure(
			String message,
			String sqlState,
			int vendorCode) {
		return new DataIntegrityViolationException(
			"unexpected persistence failure",
			new SQLIntegrityConstraintViolationException(message, sqlState, vendorCode));
	}

	private ClaimedOAuthAttempt attempt(PaymentEnvironment environment) {
		return new ClaimedOAuthAttempt(
			50L,
			UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
			1L,
			TENANT_PUBLIC_ID,
			"tienda-a",
			7L,
			USER_PUBLIC_ID,
			environment,
			new EncryptedSecret("plain", new byte[] {1}, bytes("verifier")),
			NOW.plusSeconds(600),
			1L);
	}

	private OAuthTokenResponse token(String sellerId) {
		return token(sellerId, false);
	}

	private OAuthTokenResponse token(String sellerId, boolean liveMode) {
		return new OAuthTokenResponse(
			"access-token", "refresh-token", "Bearer", Duration.ofHours(1),
			Set.of("read", "write", "offline_access"), sellerId, liveMode);
	}

	private StoredMerchantConnection connection(
			MerchantConnectionStatus status,
			String sellerId,
			String nickname) {
		return new StoredMerchantConnection(
			10L,
			UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
			1L,
			TENANT_PUBLIC_ID,
			PaymentEnvironment.TEST,
			status,
			sellerId,
			nickname,
			new EncryptedSecret("plain", new byte[] {1}, bytes("access-token")),
			new EncryptedSecret("plain", new byte[] {1}, bytes("refresh-token")),
			NOW.plusSeconds(3600),
			NOW.minusSeconds(60),
			1L);
	}

	private StoredMerchantConnection connectionForTenant(long tenantId, String sellerId) {
		return new StoredMerchantConnection(
			10L,
			UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
			tenantId,
			TENANT_PUBLIC_ID,
			PaymentEnvironment.TEST,
			MerchantConnectionStatus.CONNECTED,
			sellerId,
			"SELLER",
			new EncryptedSecret("plain", new byte[] {1}, bytes("access-token")),
			new EncryptedSecret("plain", new byte[] {1}, bytes("refresh-token")),
			NOW.plusSeconds(3600),
			NOW.minusSeconds(60),
			1L);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static final class PlainTestCipher implements CredentialCipher {
		@Override
		public EncryptedSecret encrypt(String plaintext, EncryptionContext context) {
			return new EncryptedSecret("plain", new byte[12], bytes(plaintext));
		}

		@Override
		public String decrypt(EncryptedSecret encryptedSecret, EncryptionContext context) {
			return new String(encryptedSecret.ciphertext(), StandardCharsets.UTF_8);
		}
	}
}
