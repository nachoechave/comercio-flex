package com.comercioflex.platformadmin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.EmailNormalizer;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.UserCredentials;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.platformadmin.domain.CompanyDetail;
import com.comercioflex.platformadmin.domain.CompanyStatus;
import com.comercioflex.platformadmin.domain.PrimaryAdministrator;

class CompanyProvisioningServiceTests {

	private final CompanyCreationRepository creationRepository = mock(CompanyCreationRepository.class);
	private final CompanyRepository companyRepository = mock(CompanyRepository.class);
	private final TenantProvisioner provisioner = mock(TenantProvisioner.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private CompanyProvisioningService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactions).executeWithoutResult(any());
		when(passwordEncoder.encode(any())).thenReturn("encoded-password");
		service = new CompanyProvisioningService(
			creationRepository, companyRepository, provisioner, transactions,
			passwordEncoder, new EmailNormalizer());
	}

	@Test
	void rejectsCreationBeforeWritingControlStateWhenProviderIsUnavailable() {
		when(provisioner.capability()).thenReturn(TenantProvisioningCapability.unavailable(
			"MANAGED_MYSQL", "Activá TENANT_PROVISIONING_ENABLED."));

		assertThatThrownBy(() -> service.create(command(), actor()))
			.isInstanceOf(CompanyProvisioningUnavailableException.class)
			.hasMessage("Activá TENANT_PROVISIONING_ENABLED.");

		verifyNoInteractions(creationRepository, companyRepository);
		verify(provisioner, never()).provision(any());
	}

	@Test
	void recordsSafeStepFailureAndNeverMarksCompanyReady() {
		PendingCompany pending = pending();
		when(provisioner.capability()).thenReturn(
			TenantProvisioningCapability.available("MANAGED_MYSQL"));
		when(provisioner.databaseNameFor(any())).thenReturn(pending.databaseName());
		when(creationRepository.createPending(
			any(), any(), any(), any(), any(), eq(pending.databaseName())))
			.thenReturn(pending);
		doThrow(new TenantProvisioningStepException(
			"No se pudieron aplicar las migraciones del tenant.",
			new IllegalStateException("technical detail")))
			.when(provisioner).provision(pending);

		assertThatThrownBy(() -> service.create(command(), actor()))
			.isInstanceOf(CompanyProvisioningException.class)
			.hasMessage("No se pudieron aplicar las migraciones del tenant.");

		verify(creationRepository).markFailed(
			eq(pending), any(), eq("No se pudieron aplicar las migraciones del tenant."));
		verify(creationRepository, never()).markReady(any(), any());
		verifyNoInteractions(companyRepository);
	}

	@Test
	void retriesFailedProvisioningAndMarksItReadyOnlyAfterProviderSucceeds() {
		PendingCompany pending = pending();
		CompanyDetail detail = detail(pending.publicId());
		when(provisioner.capability()).thenReturn(
			TenantProvisioningCapability.available("MANAGED_MYSQL"));
		when(creationRepository.lockProvisioningCompany(pending.publicId()))
			.thenReturn(Optional.of(pending));
		when(companyRepository.findById(pending.publicId())).thenReturn(Optional.of(detail));

		CompanyDetail result = service.retry(pending.publicId(), actor());

		assertThat(result).isEqualTo(detail);
		verify(provisioner).provision(pending);
		verify(creationRepository).markReady(eq(pending), any());
		verify(creationRepository, never()).markFailed(any(), any(), any());
	}

	private CreateCompanyCommand command() {
		return new CreateCompanyCommand(
			"Tienda Nueva", "tienda-nueva", "Retail", "owner@example.com",
			"Owner", null, null, "Strong-password-2026", CompanyStatus.ACTIVE);
	}

	private PendingCompany pending() {
		UUID id = UUID.randomUUID();
		return new PendingCompany(
			42L, id, "tenant-" + id.toString().replace("-", ""),
			"comercio_flex_tenant_" + id.toString().replace("-", ""),
			"Tienda Nueva", "owner@example.com", null, CompanyStatus.ACTIVE);
	}

	private CompanyDetail detail(UUID id) {
		return new CompanyDetail(
			id, "Tienda Nueva", "tienda-nueva", "Retail", null, CompanyStatus.ACTIVE,
			new PrimaryAdministrator("Owner", "owner@example.com"), null,
			Instant.parse("2026-08-18T12:00:00Z"), null);
	}

	private PlatformPrincipal actor() {
		return new PlatformPrincipal(new UserCredentials(
			11L, UUID.randomUUID(), "admin@example.com", "Admin", "hash", UserStatus.ACTIVE));
	}
}
