package com.comercioflex.platformadmin.application;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyInfrastructure;
import com.comercioflex.platformadmin.domain.CompanyStatus;
import com.comercioflex.platformadmin.domain.CompanyUser;

@Service
public class CompanyService {

	private final CompanyRepository repository;
	private final TransactionTemplate transactionTemplate;

	public CompanyService(
			CompanyRepository repository,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
	}

	public CompanyDashboard dashboard() {
		return repository.dashboard();
	}

	public CompanyPage findPage(CompanySearch search) {
		String query = search.query() == null ? null : search.query().strip();
		return repository.findPage(new CompanySearch(
			search.page(),
			search.size(),
			search.status(),
			query == null || query.isEmpty() ? null : query));
	}

	public CompanyDetail findById(UUID companyId) {
		return repository.findById(companyId).orElseThrow(CompanyNotFoundException::new);
	}

	public List<CompanyUser> users(UUID companyId) {
		findById(companyId);
		return repository.findUsers(companyId);
	}

	public CompanyActivityPage activity(UUID companyId, int page, int size) {
		findById(companyId);
		return repository.findActivity(companyId, page, size);
	}

	public CompanyInfrastructure infrastructure(UUID companyId) {
		return repository.findInfrastructure(companyId)
			.orElseThrow(CompanyNotFoundException::new);
	}

	public CompanyDetail update(
			UUID companyId,
			UpdateCompanyCommand rawCommand,
			PlatformPrincipal actor) {
		UpdateCompanyCommand command = normalize(rawCommand);
		try {
			return transactionTemplate.execute(status -> {
				LockedCompany company = repository.lockById(companyId)
					.orElseThrow(CompanyNotFoundException::new);
				if (sameDetails(company, command)) {
					return findById(companyId);
				}
				repository.updateDetails(company.internalId(), command);
				repository.appendDetailsAudit(company.internalId(), actor.id(), command);
				return findById(companyId);
			});
		}
		catch (DataIntegrityViolationException exception) {
			throw new CompanyUpdateConflictException(
				"El dominio indicado ya está asociado a otra empresa.");
		}
	}

	public CompanyDetail activate(UUID companyId, PlatformPrincipal actor) {
		return changeStatus(companyId, actor, CompanyStatus.ACTIVE, "COMPANY_ACTIVATED");
	}

	public CompanyDetail suspend(UUID companyId, PlatformPrincipal actor) {
		return changeStatus(companyId, actor, CompanyStatus.SUSPENDED, "COMPANY_SUSPENDED");
	}

	private CompanyDetail changeStatus(
			UUID companyId,
			PlatformPrincipal actor,
			CompanyStatus target,
			String action) {
		return transactionTemplate.execute(status -> {
			LockedCompany company = repository.lockById(companyId)
				.orElseThrow(CompanyNotFoundException::new);
			if (company.status() == target) {
				return findById(companyId);
			}
			validateTransition(company.status(), target);
			repository.updateStatus(company.internalId(), target);
			repository.appendStatusAudit(
				company.internalId(), actor.id(), action, company.status(), target);
			return findById(companyId);
		});
	}

	private void validateTransition(CompanyStatus current, CompanyStatus target) {
		if (current == CompanyStatus.PROVISIONING
				|| current == CompanyStatus.PROVISIONING_FAILED) {
			throw new CompanyStatusConflictException(
				"Una empresa en aprovisionamiento no puede cambiarse manualmente.");
		}
		if (target == CompanyStatus.SUSPENDED && current != CompanyStatus.ACTIVE) {
			throw new CompanyStatusConflictException(
				"Sólo una empresa activa puede suspenderse.");
		}
		if (target == CompanyStatus.ACTIVE
				&& current != CompanyStatus.INACTIVE
				&& current != CompanyStatus.SUSPENDED) {
			throw new CompanyStatusConflictException(
				"La empresa no puede activarse desde su estado actual.");
		}
	}

	private UpdateCompanyCommand normalize(UpdateCompanyCommand command) {
		return new UpdateCompanyCommand(
			command.name().strip(),
			command.industry().strip(),
			nullIfBlank(command.phone()),
			normalizeDomain(command.domain()));
	}

	private boolean sameDetails(LockedCompany company, UpdateCompanyCommand command) {
		return company.name().equals(command.name())
			&& Objects.equals(company.industry(), command.industry())
			&& Objects.equals(company.phone(), command.phone())
			&& Objects.equals(company.domain(), command.domain());
	}

	private String normalizeDomain(String domain) {
		String value = nullIfBlank(domain);
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	private String nullIfBlank(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
