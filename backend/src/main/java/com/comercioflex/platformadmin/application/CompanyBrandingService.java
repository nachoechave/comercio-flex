package com.comercioflex.platformadmin.application;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.tenant.application.BrandingAssetService;
import com.comercioflex.tenant.application.TenantBrandingService;
import com.comercioflex.tenant.application.TenantConnectionCatalog;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.application.UpdateTenantBrandingCommand;
import com.comercioflex.tenant.domain.BrandAssetType;

@Service
public class CompanyBrandingService {

	private final CompanyBrandingRepository repository;
	private final TenantConnectionCatalog connectionCatalog;
	private final TenantContext tenantContext;
	private final TenantBrandingService brandingService;
	private final BrandingAssetService assetService;
	private final TransactionTemplate controlTransactions;

	public CompanyBrandingService(
			CompanyBrandingRepository repository,
			TenantConnectionCatalog connectionCatalog,
			TenantContext tenantContext,
			TenantBrandingService brandingService,
			BrandingAssetService assetService,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions) {
		this.repository = repository;
		this.connectionCatalog = connectionCatalog;
		this.tenantContext = tenantContext;
		this.brandingService = brandingService;
		this.assetService = assetService;
		this.controlTransactions = controlTransactions;
	}

	public CompanyBranding find(UUID companyId) {
		BrandingCompany company = requireCompany(companyId);
		return new CompanyBranding(company.slug(), inTenant(company, brandingService::findCurrent));
	}

	public CompanyBranding update(
			UUID companyId,
			UpdateTenantBrandingCommand command,
			PlatformPrincipal actor) {
		BrandingCompany company = requireCompany(companyId);
		var branding = inTenant(company, () -> brandingService.update(command));
		audit(actor, company, "COMPANY_BRANDING_UPDATED", null, branding.template().name());
		return new CompanyBranding(company.slug(), branding);
	}

	public CompanyBranding replaceAsset(
			UUID companyId,
			BrandAssetType type,
			byte[] source,
			PlatformPrincipal actor) {
		BrandingCompany company = requireCompany(companyId);
		inTenant(company, () -> assetService.replace(type, source));
		audit(actor, company, "COMPANY_BRANDING_ASSET_UPDATED", type.name(), null);
		return find(companyId);
	}

	public CompanyBranding deleteAsset(
			UUID companyId,
			BrandAssetType type,
			PlatformPrincipal actor) {
		BrandingCompany company = requireCompany(companyId);
		inTenant(company, () -> {
			assetService.delete(type);
			return null;
		});
		audit(actor, company, "COMPANY_BRANDING_ASSET_DELETED", type.name(), null);
		return find(companyId);
	}

	private BrandingCompany requireCompany(UUID companyId) {
		BrandingCompany company = repository.findCompany(companyId)
			.orElseThrow(CompanyNotFoundException::new);
		if (!connectionCatalog.contains(company.databaseKey())) {
			throw new CompanyNotFoundException();
		}
		return company;
	}

	private <T> T inTenant(BrandingCompany company, Supplier<T> operation) {
		try (TenantContext.Scope ignored = tenantContext.open(company.databaseKey())) {
			return operation.get();
		}
	}

	private void audit(
			PlatformPrincipal actor,
			BrandingCompany company,
			String action,
			String assetType,
			String template) {
		controlTransactions.executeWithoutResult(status -> repository.appendAudit(
			actor.id(), company.internalId(), action, assetType, template));
	}
}
