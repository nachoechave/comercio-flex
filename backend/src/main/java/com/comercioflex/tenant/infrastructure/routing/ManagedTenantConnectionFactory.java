package com.comercioflex.tenant.infrastructure.routing;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ManagedTenantConnectionFactory {

	private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("[a-z0-9_]{1,64}");
	private static final String DATABASE_TOKEN = "{database}";

	private final TenantDatabaseProperties properties;

	public ManagedTenantConnectionFactory(TenantDatabaseProperties properties) {
		this.properties = properties;
	}

	public String newDatabaseName(UUID tenantId) {
		String databaseName = managed().getDatabasePrefix()
			+ tenantId.toString().replace("-", "");
		validateDatabaseName(databaseName);
		return databaseName;
	}

	public String urlFor(String databaseName) {
		validateDatabaseName(databaseName);
		String template = managed().getUrlTemplate();
		if (isBlank(template) || !template.contains(DATABASE_TOKEN)) {
			throw new IllegalStateException(
				"TENANT_DB_URL_TEMPLATE must contain {database}");
		}
		return template.replace(DATABASE_TOKEN, databaseName);
	}

	public TenantDatabaseProperties.ConnectionDetails applicationDetails(String databaseName) {
		TenantDatabaseProperties.ConnectionDetails details = new TenantDatabaseProperties.ConnectionDetails();
		details.setUrl(urlFor(databaseName));
		details.setUsername(managed().getApplicationUsername());
		details.setPassword(managed().getApplicationPassword());
		if (isBlank(details.getUsername()) || isBlank(details.getPassword())) {
			throw new IllegalStateException("Managed tenant application credentials are required");
		}
		return details;
	}

	public void requireProvisioningEnabled() {
		if (!managed().isProvisioningEnabled()) {
			throw new IllegalStateException("Managed tenant provisioning is disabled");
		}
		if (isBlank(managed().getProvisioningUrl())
				|| isBlank(managed().getProvisioningUsername())
				|| isBlank(managed().getProvisioningPassword())) {
			throw new IllegalStateException("Tenant provisioning credentials are required");
		}
		applicationDetails(newDatabaseName(UUID.randomUUID()));
	}

	public TenantDatabaseProperties.ManagedConnections managed() {
		return properties.getManaged();
	}

	private void validateDatabaseName(String databaseName) {
		if (databaseName == null || !SAFE_DATABASE_NAME.matcher(databaseName).matches()) {
			throw new IllegalStateException("Invalid managed tenant database name");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
