package com.comercioflex.platformadmin.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.platformadmin.domain.CompanyUser;

public record CompanyUserResponse(
	UUID id,
	String name,
	String email,
	MembershipRole role,
	MembershipStatus membershipStatus,
	UserStatus userStatus,
	Instant joinedAt) {

	static CompanyUserResponse from(CompanyUser user) {
		return new CompanyUserResponse(
			user.id(), user.name(), user.email(), user.role(),
			user.membershipStatus(), user.userStatus(), user.joinedAt());
	}
}
