package com.comercioflex.payment.application;

public record EncryptionContext(
	String tenantPublicId,
	String provider,
	String environment,
	String subjectId,
	String fieldName) {

	public EncryptionContext {
		require(tenantPublicId, "tenantPublicId");
		require(provider, "provider");
		require(environment, "environment");
		require(subjectId, "subjectId");
		require(fieldName, "fieldName");
	}

	public byte[] additionalAuthenticatedData(String keyId) {
		require(keyId, "keyId");
		String[] values = {
			"payment-secret:v1",
			tenantPublicId,
			provider,
			environment,
			subjectId,
			fieldName,
			keyId
		};
		byte[][] encoded = java.util.Arrays.stream(values)
			.map(value -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
			.toArray(byte[][]::new);
		int size = java.util.Arrays.stream(encoded)
			.mapToInt(value -> Integer.BYTES + value.length)
			.sum();
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(size);
		for (byte[] value : encoded) {
			buffer.putInt(value.length);
			buffer.put(value);
		}
		return buffer.array();
	}

	private static void require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " es obligatorio.");
		}
	}
}
