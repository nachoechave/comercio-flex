package com.comercioflex.payment.application;

public interface CredentialCipher {

	EncryptedSecret encrypt(String plaintext, EncryptionContext context);

	String decrypt(EncryptedSecret encryptedSecret, EncryptionContext context);
}
