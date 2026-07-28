package com.comercioflex.identity.api;

public record CsrfTokenResponse(
	String headerName,
	String parameterName,
	String token) {
}
