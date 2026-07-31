package com.comercioflex.payment.application;

import java.net.URI;

import com.comercioflex.payment.domain.PaymentEnvironment;

public interface MerchantOAuthClient {

	URI authorizationUri(String state, String codeChallenge);

	OAuthTokenResponse exchange(String code, String codeVerifier);

	OAuthTokenResponse refresh(String refreshToken);

	SellerAccountProfile fetchSellerProfile(String accessToken);

	PaymentEnvironment environment();
}
