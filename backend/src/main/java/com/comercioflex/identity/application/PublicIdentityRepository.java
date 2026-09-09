package com.comercioflex.identity.application;

import java.time.Instant;
import java.util.Optional;

public interface PublicIdentityRepository {

	void create(String firstName, String lastName, String email, String phone, String passwordHash);
	PublicProfile profile(long userId);
	void update(long userId, String firstName, String lastName, String phone);
	Optional<Long> activeUser(String email);
	void storeReset(long userId, long tenantId, byte[] hash, Instant expiresAt);
	Optional<Long> resetUser(byte[] hash, long tenantId, Instant now);
	void lockUser(long userId);
	boolean consumeReset(byte[] hash, long tenantId, Instant now);
	void changePasswordAndRevokeSessions(long userId, String passwordHash);
}
