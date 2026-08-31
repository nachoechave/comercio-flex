package com.comercioflex.payment.domain;

public enum PaymentIntentStatus {
	CREATED,
	PENDING,
	APPROVED,
	REJECTED,
	EXPIRED,
	REQUIRES_REVIEW
}
