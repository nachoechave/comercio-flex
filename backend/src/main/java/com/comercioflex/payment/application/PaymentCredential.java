package com.comercioflex.payment.application;

import com.comercioflex.payment.domain.PaymentEnvironment;

public record PaymentCredential(
	String accessToken,
	String sellerAccountId,
	PaymentEnvironment environment,
	Source source) {

	@Override
	public String toString() {
		return "PaymentCredential[accessToken=<redacted>, sellerAccountId="
			+ sellerAccountId + ", environment=" + environment + ", source=" + source + "]";
	}

	public enum Source {
		CENTRAL_TEST,
		TENANT_OAUTH
	}
}
