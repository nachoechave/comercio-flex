package com.comercioflex.tenant.application;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.comercioflex.tenant.infrastructure.control.TenantRepository;

@Service
public class TenantResolver {

	private static final Pattern SAFE_SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?");

	private final TenantRepository tenantRepository;
	private final TenantConnectionCatalog connectionCatalog;

	public TenantResolver(
			TenantRepository tenantRepository,
			TenantConnectionCatalog connectionCatalog) {
		this.tenantRepository = tenantRepository;
		this.connectionCatalog = connectionCatalog;
	}

	@Transactional(readOnly = true)
	public ResolvedTenant resolveActive(String slug) {
		if (slug == null || !SAFE_SLUG.matcher(slug).matches()) {
			throw new TenantNotFoundException();
		}

		return tenantRepository.findActiveBySlug(slug)
			.filter(tenant -> connectionCatalog.contains(tenant.databaseKey()))
			.map(tenant -> new ResolvedTenant(
				tenant.id(),
				tenant.slug(),
				tenant.displayName(),
				tenant.databaseKey()))
			.orElseThrow(TenantNotFoundException::new);
	}
}
