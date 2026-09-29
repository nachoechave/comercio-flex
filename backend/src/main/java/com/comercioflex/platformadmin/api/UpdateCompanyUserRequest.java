package com.comercioflex.platformadmin.api;

import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateCompanyUserRequest(
	@NotNull MembershipRole role,
	@NotNull MembershipStatus membershipStatus) {
}
