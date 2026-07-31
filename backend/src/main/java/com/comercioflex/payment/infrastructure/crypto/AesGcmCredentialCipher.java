package com.comercioflex.payment.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.comercioflex.payment.application.CredentialCipher;
import com.comercioflex.payment.application.CredentialCipherException;
import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.payment.application.EncryptionContext;

public final class AesGcmCredentialCipher implements CredentialCipher {

	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int AES_256_BYTES = 32;

	private final String activeKeyId;
	private final Map<String, SecretKey> keys;
	private final SecureRandom secureRandom;

	public AesGcmCredentialCipher(
			String activeKeyId,
			Map<String, SecretKey> keys) {
		this(activeKeyId, keys, new SecureRandom());
	}

	AesGcmCredentialCipher(
			String activeKeyId,
			Map<String, SecretKey> keys,
			SecureRandom secureRandom) {
		this.activeKeyId = requireText(activeKeyId, "activeKeyId");
		this.keys = Map.copyOf(keys);
		this.secureRandom = Objects.requireNonNull(secureRandom);
		SecretKey activeKey = this.keys.get(activeKeyId);
		if (activeKey == null) {
			throw new IllegalArgumentException("La clave activa no existe en el key ring.");
		}
		this.keys.forEach((keyId, key) -> validateKey(keyId, key));
	}

	public static SecretKey decodeAes256Key(String base64) {
		try {
			byte[] decoded = Base64.getDecoder().decode(requireText(base64, "base64"));
			if (decoded.length != AES_256_BYTES) {
				throw new IllegalArgumentException("La clave debe contener 32 bytes.");
			}
			return new SecretKeySpec(decoded, "AES");
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("La clave AES-256 no es válida.", exception);
		}
	}

	@Override
	public EncryptedSecret encrypt(String plaintext, EncryptionContext context) {
		if (plaintext == null || plaintext.isEmpty()) {
			throw new IllegalArgumentException("El secreto no puede estar vacío.");
		}
		byte[] nonce = new byte[NONCE_BYTES];
		secureRandom.nextBytes(nonce);
		try {
			Cipher cipher = cipher(
				Cipher.ENCRYPT_MODE,
				keys.get(activeKeyId),
				nonce,
				context,
				activeKeyId);
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return new EncryptedSecret(activeKeyId, nonce, ciphertext);
		}
		catch (GeneralSecurityException exception) {
			throw new CredentialCipherException("No se pudo proteger la credencial.", exception);
		}
	}

	@Override
	public String decrypt(EncryptedSecret encryptedSecret, EncryptionContext context) {
		Objects.requireNonNull(encryptedSecret);
		SecretKey key = keys.get(encryptedSecret.keyId());
		if (key == null) {
			throw new CredentialCipherException("La credencial no puede descifrarse.");
		}
		if (encryptedSecret.nonce().length != NONCE_BYTES
				|| encryptedSecret.ciphertext().length < TAG_BITS / Byte.SIZE) {
			throw new CredentialCipherException("La credencial no puede descifrarse.");
		}
		try {
			Cipher cipher = cipher(
				Cipher.DECRYPT_MODE,
				key,
				encryptedSecret.nonce(),
				context,
				encryptedSecret.keyId());
			return new String(
				cipher.doFinal(encryptedSecret.ciphertext()),
				StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException exception) {
			throw new CredentialCipherException("La credencial no puede descifrarse.");
		}
	}

	private Cipher cipher(
			int mode,
			SecretKey key,
			byte[] nonce,
			EncryptionContext context,
			String keyId) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
		cipher.updateAAD(context.additionalAuthenticatedData(keyId));
		return cipher;
	}

	private void validateKey(String keyId, SecretKey key) {
		requireText(keyId, "keyId");
		if (key == null
				|| !"AES".equalsIgnoreCase(key.getAlgorithm())
				|| key.getEncoded() == null
				|| key.getEncoded().length != AES_256_BYTES) {
			throw new IllegalArgumentException(
				"Cada clave del key ring debe ser AES-256.");
		}
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " es obligatorio.");
		}
		return value;
	}
}
