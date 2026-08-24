package com.comercioflex.tenant.application;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.domain.StoreSettings;

@Service
public class StoreSettingsService {

	private final StoreSettingsRepository repository;
	private final TransactionTemplate transactionTemplate;

	public StoreSettingsService(
			StoreSettingsRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
	}

	public StoreSettings update(UpdateStoreSettingsCommand command) {
		UpdateStoreSettingsCommand normalized = new UpdateStoreSettingsCommand(
			command.storeName().trim(),
			nullIfBlank(command.contactPhone()),
			nullIfBlank(command.contactEmail()),
			command.pickupAddress().trim(),
			nullIfBlank(command.pickupInstructions()),
			command.bankTransferEnabled(),
			nullIfBlank(command.bankName()),
			nullIfBlank(command.bankAccountHolder()),
			nullIfBlank(command.bankAlias()),
			nullIfBlank(command.bankCbuCvu()));
		return transactionTemplate.execute(status -> {
			if (repository.findCurrent().isEmpty()) {
				throw new TenantNotFoundException();
			}
			repository.update(normalized);
			return repository.findCurrent().orElseThrow(TenantNotFoundException::new);
		});
	}

	private static String nullIfBlank(String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim();
	}
}
