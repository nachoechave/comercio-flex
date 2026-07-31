package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface MerchantOAuthRepository {

	OAuthTenantIdentity requireActiveTenant(long tenantId, String slug);

	void supersedePending(long tenantId, long userId, PaymentEnvironment environment, Instant now);

	void insertAttempt(
		UUID attemptId,
		long tenantId,
		long userId,
		UUID userPublicId,
		PaymentEnvironment environment,
		byte[] stateHash,
		EncryptedSecret pkceVerifier,
		Instant expiresAt);

	Optional<ClaimedOAuthAttempt> claimAttempt(
		byte[] stateHash,
		long currentUserId,
		PaymentEnvironment environment,
		Instant now);

	void markAttemptFailed(long attemptInternalId, String failureCode, Instant now);

	void markAttemptSucceeded(long attemptInternalId, Instant now);

	boolean hasPendingAttempt(long tenantId, PaymentEnvironment environment, Instant now);

	Optional<StoredMerchantConnection> findConnection(
		long tenantId,
		PaymentEnvironment environment,
		boolean forUpdate);

	Optional<StoredMerchantConnection> findActiveBySeller(
		String sellerAccountId,
		PaymentEnvironment environment,
		boolean forUpdate);

	long upsertConnected(
		UUID connectionId,
		long tenantId,
		PaymentEnvironment environment,
		String sellerAccountId,
		String sellerNickname,
		Set<String> scopes,
		EncryptedSecret accessToken,
		EncryptedSecret refreshToken,
		Instant accessTokenExpiresAt,
		long connectedByUserId,
		UUID connectedByUserPublicId,
		UUID oauthAttemptId,
		Instant now,
		Optional<StoredMerchantConnection> existing);

	void disconnect(
		StoredMerchantConnection connection,
		long actorUserId,
		UUID actorUserPublicId,
		Instant now);

	void replaceRefreshedTokens(
		StoredMerchantConnection connection,
		Set<String> scopes,
		EncryptedSecret accessToken,
		EncryptedSecret refreshToken,
		Instant accessTokenExpiresAt,
		Instant now);

	void requireReauthorization(
		StoredMerchantConnection connection,
		String errorCode,
		Instant now);
}
