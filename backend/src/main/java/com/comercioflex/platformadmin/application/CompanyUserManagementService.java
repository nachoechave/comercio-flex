package com.comercioflex.platformadmin.application;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.EmailNormalizer;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.domain.MembershipRole;
import com.comercioflex.identity.domain.MembershipStatus;
import com.comercioflex.platformadmin.domain.CompanyUser;

@Service
public class CompanyUserManagementService {

	private static final Set<MembershipRole> ASSIGNABLE_ROLES = EnumSet.of(
		MembershipRole.ADMIN,
		MembershipRole.MANAGER,
		MembershipRole.SELLER,
		MembershipRole.STAFF);

	private final CompanyUserManagementRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final EmailNormalizer emailNormalizer;
	private final TransactionTemplate transactionTemplate;

	public CompanyUserManagementService(
			CompanyUserManagementRepository repository,
			PasswordEncoder passwordEncoder,
			EmailNormalizer emailNormalizer,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.emailNormalizer = emailNormalizer;
		this.transactionTemplate = transactionTemplate;
	}

	public CompanyUser create(
			UUID companyId,
			String rawName,
			String rawEmail,
			String rawPassword,
			MembershipRole role,
			PlatformPrincipal actor) {
		validateAssignableRole(role);
		String name = rawName.strip();
		String email = emailNormalizer.normalize(rawEmail);
		try {
			return transactionTemplate.execute(status -> {
				long tenantId = repository.findTenantInternalId(companyId)
					.orElseThrow(CompanyNotFoundException::new);
				if (repository.emailExists(email)) {
					throw new CompanyUserConflictException(
						"Ya existe una cuenta global con ese email. Usá otro correo para este acceso.");
				}
				UUID userId = UUID.randomUUID();
				long userInternalId = repository.insertUser(
					userId, name, email, passwordEncoder.encode(rawPassword));
				repository.insertMembership(userInternalId, tenantId, role);
				repository.appendAudit(
					tenantId, actor.id(), "COMPANY_USER_CREATED", userId, role, MembershipStatus.ACTIVE);
				return repository.findUser(companyId, userId)
					.orElseThrow(CompanyUserNotFoundException::new);
			});
		}
		catch (DataIntegrityViolationException exception) {
			throw new CompanyUserConflictException(
				"No pudimos crear el usuario porque el email o la membresía ya están en uso.");
		}
	}

	public CompanyUser update(
			UUID companyId,
			UUID userId,
			MembershipRole role,
			MembershipStatus membershipStatus,
			PlatformPrincipal actor) {
		validateAssignableRole(role);
		return transactionTemplate.execute(status -> {
			long tenantId = repository.findTenantInternalId(companyId)
				.orElseThrow(CompanyNotFoundException::new);
			CompanyUser current = repository.findUser(companyId, userId)
				.orElseThrow(CompanyUserNotFoundException::new);
			if (current.role() == MembershipRole.OWNER) {
				throw new CompanyUserConflictException(
					"El propietario principal se administra desde la empresa y no puede modificarse aquí.");
			}
			if (current.role() == role && current.membershipStatus() == membershipStatus) {
				return current;
			}
			repository.updateMembership(tenantId, userId, role, membershipStatus);
			repository.appendAudit(
				tenantId, actor.id(), "COMPANY_USER_UPDATED", userId, role, membershipStatus);
			return repository.findUser(companyId, userId)
				.orElseThrow(CompanyUserNotFoundException::new);
		});
	}

	private void validateAssignableRole(MembershipRole role) {
		if (!ASSIGNABLE_ROLES.contains(role)) {
			throw new CompanyUserConflictException(
				"El rol propietario no puede asignarse desde la administración de usuarios.");
		}
	}
}
