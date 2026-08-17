package com.comercioflex.tenant.application;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.domain.TenantBranding;

@Service
public class TenantBrandingService {

	private final TenantBrandingRepository repository;
	private final TransactionTemplate transactions;

	public TenantBrandingService(
			TenantBrandingRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactions) {
		this.repository = repository;
		this.transactions = transactions;
	}

	public TenantBranding findCurrent() {
		return transactions.execute(status -> repository.findCurrent()
			.orElseThrow(TenantNotFoundException::new));
	}

	public TenantBranding update(UpdateTenantBrandingCommand command) {
		UpdateTenantBrandingCommand normalized = new UpdateTenantBrandingCommand(
			command.primaryColor().toUpperCase(java.util.Locale.ROOT),
			command.secondaryColor().toUpperCase(java.util.Locale.ROOT),
			command.backgroundColor().toUpperCase(java.util.Locale.ROOT),
			command.textColor().toUpperCase(java.util.Locale.ROOT),
			command.font(), nullIfBlank(command.heroTitle()),
			nullIfBlank(command.heroSubtitle()), command.template());
		return transactions.execute(status -> {
			if (repository.findCurrent().isEmpty()) throw new TenantNotFoundException();
			repository.update(normalized);
			return repository.findCurrent().orElseThrow(TenantNotFoundException::new);
		});
	}

	private String nullIfBlank(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
