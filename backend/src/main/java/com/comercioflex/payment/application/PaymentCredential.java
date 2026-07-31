package com.comercioflex.payment.application;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record PaymentCredential(
	String accessToken,
	String sellerAccountId,
	PaymentEnvironment environment,
	Source source) {

	public enum Source {
		CENTRAL_TEST,
		TENANT_OAUTH
	}
}
