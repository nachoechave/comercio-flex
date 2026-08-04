package com.comercioflex.tenant.application;

import java.util.Optional;

import com.comercioflex.tenant.domain.StoreSettings;

public interface StoreSettingsRepository {
	Optional<StoreSettings> findCurrent();

	void update(UpdateStoreSettingsCommand command);
}
