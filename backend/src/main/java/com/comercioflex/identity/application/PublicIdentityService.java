package com.comercioflex.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantNotFoundException;
import com.comercioflex.tenant.domain.TenantType;

@Service
public class PublicIdentityService {

	private final PublicIdentityRepository repository;
	private final PasswordEncoder passwords;
	private final EmailNormalizer emails;
	private final TransactionTemplate transactions;

	public PublicIdentityService(PublicIdentityRepository repository, PasswordEncoder passwords,
			EmailNormalizer emails, @Qualifier("controlTransactionTemplate") TransactionTemplate transactions) {
		this.repository = repository;
		this.passwords = passwords;
		this.emails = emails;
		this.transactions = transactions;
	}

	public static void requireRadio(ResolvedTenant tenant) {
		if (tenant.tenantType() != TenantType.RADIO) throw new TenantNotFoundException();
	}

	public void register(String firstName, String lastName, String email, String phone, String password) {
		validatePassword(password);
		String hash = passwords.encode(password);
		try {
			transactions.executeWithoutResult(status -> repository.create(firstName.strip(), lastName.strip(),
				emails.normalize(email), cleanPhone(phone), hash));
		}
		catch (DuplicateKeyException duplicate) {
			// Do not disclose global identity existence or modify its credentials/roles.
		}
	}

	public PublicProfile profile(long userId) {
		return transactions.execute(status -> repository.profile(userId));
	}

	public PublicProfile update(long userId, String firstName, String lastName, String phone) {
		return transactions.execute(status -> {
			repository.update(userId, firstName.strip(), lastName.strip(), cleanPhone(phone));
			return repository.profile(userId);
		});
	}

	public void reset(long tenantId, String token, String password) {
		validatePassword(password);
		String encoded = passwords.encode(password);
		byte[] hash = hash(token);
		transactions.executeWithoutResult(status -> {
			long userId = repository.resetUser(hash, tenantId, Instant.now()).orElseThrow(PublicIdentityService::invalidToken);
			repository.lockUser(userId);
			// Atomic delete checks expiration again after waiting for the user lock.
			if (!repository.consumeReset(hash, tenantId, Instant.now())) throw invalidToken();
			repository.changePasswordAndRevokeSessions(userId, encoded);
		});
	}

	public static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
	}

	private static PublicIdentityException invalidToken() {
		return new PublicIdentityException("El enlace no es válido o venció. Solicitá uno nuevo.");
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
				|| password.isBlank()) {
			throw new PublicIdentityException("Usá una contraseña de al menos 12 caracteres y hasta 72 bytes UTF-8.");
		}
	}

	private String cleanPhone(String phone) { return phone == null || phone.isBlank() ? null : phone.strip(); }
}
