package com.comercioflex.tenant.application;

import java.util.Optional;

import com.comercioflex.tenant.domain.BrandAssetReference;
import com.comercioflex.tenant.domain.BrandAssetType;
import com.comercioflex.tenant.domain.TenantBranding;

public interface TenantBrandingRepository {

	Optional<TenantBranding> findCurrent();

	void update(UpdateTenantBrandingCommand command);

	BrandAssetReference replaceAsset(BrandAssetType type, BrandAssetReference reference);

	BrandAssetReference clearAsset(BrandAssetType type);
}
