package com.comercioflex.payment.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.comercioflex.payment.application.CredentialCipherException;
import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.payment.application.EncryptionContext;

class AesGcmCredentialCipherTests {

	private static final String PLAINTEXT = "TEST-credential-never-log";
	private static final EncryptionContext CONTEXT = new EncryptionContext(
		"tenant-a",
		"MERCADO_PAGO",
		"TEST",
		"connection-1",
		"access_token");

	@Test
	void encryptsWithRandomNonceAndDecryptsWithTheSameContext() {
		AesGcmCredentialCipher cipher = cipher();

		EncryptedSecret first = cipher.encrypt(PLAINTEXT, CONTEXT);
		EncryptedSecret second = cipher.encrypt(PLAINTEXT, CONTEXT);

		assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
		assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
		assertThat(new String(first.ciphertext(), StandardCharsets.UTF_8))
			.doesNotContain(PLAINTEXT);
		assertThat(cipher.decrypt(first, CONTEXT)).isEqualTo(PLAINTEXT);
		assertThat(first.toString()).doesNotContain(PLAINTEXT);
	}

	@Test
	void rejectsTamperingWrongContextAndUnknownKeyWithoutLeakingDetails() {
		AesGcmCredentialCipher cipher = cipher();
		EncryptedSecret encrypted = cipher.encrypt(PLAINTEXT, CONTEXT);
		byte[] tampered = encrypted.ciphertext();
		tampered[tampered.length - 1] ^= 1;

		assertThatThrownBy(() -> cipher.decrypt(
			new EncryptedSecret(encrypted.keyId(), encrypted.nonce(), tampered),
			CONTEXT))
			.isInstanceOf(CredentialCipherException.class)
			.hasMessage("La credencial no puede descifrarse.")
			.hasMessageNotContaining(PLAINTEXT);
		assertThatThrownBy(() -> cipher.decrypt(
			encrypted,
			new EncryptionContext(
				"tenant-b",
				"MERCADO_PAGO",
				"TEST",
				"connection-1",
				"access_token")))
			.isInstanceOf(CredentialCipherException.class);
		assertThatThrownBy(() -> cipher.decrypt(
			new EncryptedSecret("missing", encrypted.nonce(), encrypted.ciphertext()),
			CONTEXT))
			.isInstanceOf(CredentialCipherException.class);
	}

	@Test
	void validatesAes256MaterialAndSupportsKeyRotation() {
		byte[] keyV1 = new byte[32];
		byte[] keyV2 = new byte[32];
		Arrays.fill(keyV1, (byte) 1);
		Arrays.fill(keyV2, (byte) 2);
		var v1 = AesGcmCredentialCipher.decodeAes256Key(
			Base64.getEncoder().encodeToString(keyV1));
		var v2 = AesGcmCredentialCipher.decodeAes256Key(
			Base64.getEncoder().encodeToString(keyV2));
		AesGcmCredentialCipher original = new AesGcmCredentialCipher(
			"v1", Map.of("v1", v1));
		EncryptedSecret encryptedV1 = original.encrypt(PLAINTEXT, CONTEXT);
		AesGcmCredentialCipher rotated = new AesGcmCredentialCipher(
			"v2", Map.of("v1", v1, "v2", v2));

		assertThat(rotated.decrypt(encryptedV1, CONTEXT)).isEqualTo(PLAINTEXT);
		assertThat(rotated.encrypt(PLAINTEXT, CONTEXT).keyId()).isEqualTo("v2");
		assertThatThrownBy(() -> AesGcmCredentialCipher.decodeAes256Key(
			Base64.getEncoder().encodeToString(new byte[16])))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void bindsCiphertextToTheExplicitKeyIdEvenWhenAliasesShareKeyMaterial() {
		byte[] material = new byte[32];
		Arrays.fill(material, (byte) 9);
		var sharedKey = AesGcmCredentialCipher.decodeAes256Key(
			Base64.getEncoder().encodeToString(material));
		AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(
			"v2",
			Map.of("v1", sharedKey, "v2", sharedKey));

		EncryptedSecret encrypted = cipher.encrypt(PLAINTEXT, CONTEXT);

		assertThat(encrypted.keyId()).isEqualTo("v2");
		assertThat(cipher.decrypt(encrypted, CONTEXT)).isEqualTo(PLAINTEXT);
		assertThatThrownBy(() -> cipher.decrypt(
			new EncryptedSecret("v1", encrypted.nonce(), encrypted.ciphertext()),
			CONTEXT))
			.isInstanceOf(CredentialCipherException.class);
	}

	private AesGcmCredentialCipher cipher() {
		byte[] key = new byte[32];
		Arrays.fill(key, (byte) 7);
		return new AesGcmCredentialCipher(
			"v1",
			Map.of("v1", AesGcmCredentialCipher.decodeAes256Key(
				Base64.getEncoder().encodeToString(key))));
	}
}
