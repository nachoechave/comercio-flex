package com.comercioflex.platformadmin.application;

import java.util.Optional;
import java.util.UUID;

import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;
import com.comercioflex.platformadmin.domain.CompanyUser;

public interface CompanyUserManagementRepository {

	Optional<Long> findTenantInternalId(UUID companyId);

	boolean emailExists(String normalizedEmail);

	long insertUser(UUID userId, String name, String normalizedEmail, String passwordHash);

	void insertMembership(long userInternalId, long tenantInternalId, MembershipRole role);

	Optional<CompanyUser> findUser(UUID companyId, UUID userId);

	void updateMembership(
		long tenantInternalId,
		UUID userId,
		MembershipRole role,
		MembershipStatus status);

	void appendAudit(
		long tenantInternalId,
		long actorUserId,
		String action,
		UUID userId,
		MembershipRole role,
		MembershipStatus status);
}
