package com.comercioflex.identity.application;

public class LoginRateLimitExceededException extends RuntimeException {

	public LoginRateLimitExceededException() {
		super("Too many login attempts.");
	}
}
