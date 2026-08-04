package com.comercioflex.tenant.application;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.domain.StoreSettings;

@Service
public class StoreSettingsQueryService {

	private final StoreSettingsRepository repository;
	private final TransactionTemplate tenantTransactionTemplate;

	public StoreSettingsQueryService(
			StoreSettingsRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate tenantTransactionTemplate) {
		this.repository = repository;
		this.tenantTransactionTemplate = tenantTransactionTemplate;
	}

	public StoreSettings findCurrent() {
		return tenantTransactionTemplate.execute(status -> repository.findCurrent()
			.orElseThrow(TenantNotFoundException::new));
	}
}
