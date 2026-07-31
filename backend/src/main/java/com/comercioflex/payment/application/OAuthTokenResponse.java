package com.comercioflex.payment.application;

import java.time.Duration;
import java.util.Set;

public record OAuthTokenResponse(
	String accessToken,
	String refreshToken,
	String tokenType,
	Duration expiresIn,
	Set<String> scopes,
	String sellerAccountId,
	boolean liveMode) {
}
