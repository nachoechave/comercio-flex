package com.comercioflex.platformadmin.api;

import com.comercioflex.identity.domain.MembershipRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCompanyUserRequest(
	@NotBlank @Size(max = 160) String name,
	@NotBlank @Email @Size(max = 254) String email,
	@NotBlank @Size(min = 8, max = 200) String password,
	@NotNull MembershipRole role) {

	public CreateCompanyUserRequest {
		name = name == null ? null : name.strip();
		email = email == null ? null : email.strip();
	}
}
