package com.comercioflex.payment.application;

public record ReceivedWebhook(
	String notificationId,
	String requestId,
	String eventType,
	String action,
	String providerResourceId,
	String providerUserId,
	boolean liveMode,
	byte[] payloadHash) {

	public ReceivedWebhook {
		payloadHash = payloadHash.clone();
	}

	@Override
	public byte[] payloadHash() {
		return payloadHash.clone();
	}
}
