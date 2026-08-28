package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.UserCredentials;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.payment.domain.MerchantConnectionStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

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
			eq(attempt.publicId()), eq(NOW), eq(Optional.empty()));
		assertThat(nickname.getValue()).isEqualTo("CARNES_DEL_SUR");
		verify(repository).markAttemptSucceeded(50L, NOW);
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
		return new ClaimedOAuthAttempt(
			50L,
			UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
			1L,
			TENANT_PUBLIC_ID,
			"tienda-a",
			7L,
			USER_PUBLIC_ID,
			PaymentEnvironment.TEST,
			new EncryptedSecret("plain", new byte[] {1}, bytes("verifier")),
			NOW.plusSeconds(600),
			1L);
	}

	private OAuthTokenResponse token(String sellerId) {
		return new OAuthTokenResponse(
			"access-token", "refresh-token", "Bearer", Duration.ofHours(1),
			Set.of("read", "write", "offline_access"), sellerId, false);
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

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static final class PlainTestCipher implements CredentialCipher {
		@Override
		public EncryptedSecret encrypt(String plaintext, EncryptionContext context) {
			return new EncryptedSecret("plain", new byte[] {1}, bytes(plaintext));
		}

		@Override
		public String decrypt(EncryptedSecret encryptedSecret, EncryptionContext context) {
			return new String(encryptedSecret.ciphertext(), StandardCharsets.UTF_8);
		}
	}
}
