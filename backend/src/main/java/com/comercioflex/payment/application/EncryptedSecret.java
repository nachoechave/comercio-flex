package com.comercioflex.payment.application;

import java.util.Objects;

public record EncryptedSecret(
	String keyId,
	byte[] nonce,
	byte[] ciphertext) {

	public EncryptedSecret {
		Objects.requireNonNull(keyId);
		nonce = nonce.clone();
		ciphertext = ciphertext.clone();
	}

	@Override
	public byte[] nonce() {
		return nonce.clone();
	}

	@Override
	public byte[] ciphertext() {
		return ciphertext.clone();
	}

	@Override
	public String toString() {
		return "EncryptedSecret[keyId=" + keyId + ", protected=true]";
	}
}
