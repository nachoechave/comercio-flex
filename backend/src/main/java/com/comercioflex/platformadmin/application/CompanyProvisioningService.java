package com.comercioflex.platformadmin.application;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.EmailNormalizer;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyStatus;

@Service
public class CompanyProvisioningService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CompanyProvisioningService.class);
	private static final String PUBLIC_FAILURE_REASON =
		"No se pudo completar el aprovisionamiento automático.";

	private final CompanyCreationRepository creationRepository;
	private final CompanyRepository companyRepository;
	private final TenantProvisioner provisioner;
	private final TransactionTemplate transactionTemplate;
	private final PasswordEncoder passwordEncoder;
	private final EmailNormalizer emailNormalizer;

	public CompanyProvisioningService(
			CompanyCreationRepository creationRepository,
			CompanyRepository companyRepository,
			TenantProvisioner provisioner,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactionTemplate,
			PasswordEncoder passwordEncoder,
			EmailNormalizer emailNormalizer) {
		this.creationRepository = creationRepository;
		this.companyRepository = companyRepository;
		this.provisioner = provisioner;
		this.transactionTemplate = transactionTemplate;
		this.passwordEncoder = passwordEncoder;
		this.emailNormalizer = emailNormalizer;
	}

	public CompanyDetail create(CreateCompanyCommand rawCommand, PlatformPrincipal actor) {
		requireProvisioningAvailable();
		CreateCompanyCommand command = normalize(rawCommand);
		UUID companyId = UUID.randomUUID();
		String compactId = companyId.toString().replace("-", "");
		String databaseKey = "tenant-" + compactId;
		String databaseName = provisioner.databaseNameFor(companyId);
		PendingCompany pending;
		try {
			pending = transactionTemplate.execute(status -> creationRepository.createPending(
				command,
				passwordEncoder.encode(command.initialPassword()),
				actor,
				companyId,
				databaseKey,
				databaseName));
		}
		catch (DataIntegrityViolationException exception) {
			throw new CompanyCreationConflictException(
				"El slug, dominio o relación del administrador ya está en uso.");
		}
		return provision(pending, actor);
	}

	public CompanyDetail retry(UUID companyId, PlatformPrincipal actor) {
		requireProvisioningAvailable();
		PendingCompany pending = transactionTemplate.execute(status ->
			creationRepository.lockProvisioningCompany(companyId)
				.orElseThrow(CompanyNotFoundException::new));
		return provision(pending, actor);
	}

	private CompanyDetail provision(PendingCompany pending, PlatformPrincipal actor) {
		try {
			provisioner.provision(pending);
			transactionTemplate.executeWithoutResult(status ->
				creationRepository.markReady(pending, actor));
			return companyRepository.findById(pending.publicId())
				.orElseThrow(CompanyNotFoundException::new);
		}
		catch (RuntimeException exception) {
			LOGGER.error("Managed tenant provisioning failed for company {}", pending.publicId(), exception);
			String failureReason = exception instanceof TenantProvisioningStepException stepException
				? stepException.safeReason()
				: PUBLIC_FAILURE_REASON;
			transactionTemplate.executeWithoutResult(status ->
				creationRepository.markFailed(pending, actor, failureReason));
			throw new CompanyProvisioningException(failureReason, exception);
		}
	}

	public TenantProvisioningCapability capability() {
		return provisioner.capability();
	}

	private void requireProvisioningAvailable() {
		TenantProvisioningCapability capability = provisioner.capability();
		if (!capability.available()) {
			throw new CompanyProvisioningUnavailableException(capability.reason());
		}
	}

	private CreateCompanyCommand normalize(CreateCompanyCommand command) {
		CompanyStatus requested = command.requestedStatus();
		if (requested != CompanyStatus.ACTIVE && requested != CompanyStatus.INACTIVE) {
			throw new IllegalArgumentException("Invalid initial company status");
		}
		return new CreateCompanyCommand(
			command.name().strip(),
			command.slug().strip().toLowerCase(Locale.ROOT),
			command.industry().strip(),
			emailNormalizer.normalize(command.administratorEmail()),
			command.administratorName().strip(),
			nullIfBlank(command.administratorPhone()),
			normalizeDomain(command.domain()),
			command.initialPassword(),
			requested);
	}

	private String normalizeDomain(String domain) {
		String value = nullIfBlank(domain);
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	private String nullIfBlank(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
