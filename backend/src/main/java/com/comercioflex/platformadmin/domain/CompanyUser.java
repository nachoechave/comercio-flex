package com.comercioflex.platformadmin.domain;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;
import com.comercioflex.identity.domain.UserStatus;

public record CompanyUser(
	UUID id,
	String name,
	String email,
	MembershipRole role,
	MembershipStatus membershipStatus,
	UserStatus userStatus,
	Instant joinedAt) {
}
