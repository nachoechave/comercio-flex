package com.comercioflex.identity.application;

import java.util.UUID;

import com.comercioflex.identity.domain.UserStatus;

public record UserCredentials(
	Long id,
	UUID publicId,
	String email,
	String displayName,
	String passwordHash,
	UserStatus status) {
}
