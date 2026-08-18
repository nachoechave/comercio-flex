package com.comercioflex.tenant.infrastructure.routing;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.database")
public class TenantDatabaseProperties {

	private boolean tenantMigrationEnabled;
	private String migrationUsername;
	private String migrationPassword;
	private ManagedConnections managed = new ManagedConnections();
	private Map<String, ConnectionDetails> tenantConnections = new LinkedHashMap<>();

	public boolean isTenantMigrationEnabled() {
		return tenantMigrationEnabled;
	}

	public void setTenantMigrationEnabled(boolean tenantMigrationEnabled) {
		this.tenantMigrationEnabled = tenantMigrationEnabled;
	}

	public String getMigrationUsername() {
		return migrationUsername;
	}

	public void setMigrationUsername(String migrationUsername) {
		this.migrationUsername = migrationUsername;
	}

	public String getMigrationPassword() {
		return migrationPassword;
	}

	public void setMigrationPassword(String migrationPassword) {
		this.migrationPassword = migrationPassword;
	}

	public ManagedConnections getManaged() {
		return managed;
	}

	public void setManaged(ManagedConnections managed) {
		this.managed = managed;
	}

	public Map<String, ConnectionDetails> getTenantConnections() {
		return tenantConnections;
	}

	public void setTenantConnections(Map<String, ConnectionDetails> tenantConnections) {
		this.tenantConnections = tenantConnections;
	}

	public Map<String, ConnectionDetails> configuredTenantConnections() {
		Map<String, ConnectionDetails> configured = new LinkedHashMap<>();
		tenantConnections.forEach((databaseKey, details) -> {
			boolean hasUrl = !isBlank(details.getUrl());
			boolean hasUsername = !isBlank(details.getUsername());
			boolean hasPassword = !isBlank(details.getPassword());
			if (!hasUrl && !hasUsername && !hasPassword) {
				return;
			}
			if (!hasUrl || !hasUsername || !hasPassword) {
				throw new IllegalStateException(
					"Incomplete tenant database configuration for key " + databaseKey);
			}
			configured.put(databaseKey, details);
		});
		return configured;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public static class ConnectionDetails {

		private String url;
		private String username;
		private String password;

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}
	}

	public static class ManagedConnections {

		private boolean provisioningEnabled;
		private String urlTemplate;
		private String applicationUsername;
		private String applicationPassword;
		private String provisioningUrl;
		private String provisioningUsername;
		private String provisioningPassword;
		private String databasePrefix = "comercio_flex_tenant_";
		private String databaseUserHost = "%";

		public boolean isProvisioningEnabled() {
			return provisioningEnabled;
		}

		public void setProvisioningEnabled(boolean provisioningEnabled) {
			this.provisioningEnabled = provisioningEnabled;
		}

		public String getUrlTemplate() {
			return urlTemplate;
		}

		public void setUrlTemplate(String urlTemplate) {
			this.urlTemplate = urlTemplate;
		}

		public String getApplicationUsername() {
			return applicationUsername;
		}

		public void setApplicationUsername(String applicationUsername) {
			this.applicationUsername = applicationUsername;
		}

		public String getApplicationPassword() {
			return applicationPassword;
		}

		public void setApplicationPassword(String applicationPassword) {
			this.applicationPassword = applicationPassword;
		}

		public String getProvisioningUrl() {
			return provisioningUrl;
		}

		public void setProvisioningUrl(String provisioningUrl) {
			this.provisioningUrl = provisioningUrl;
		}

		public String getProvisioningUsername() {
			return provisioningUsername;
		}

		public void setProvisioningUsername(String provisioningUsername) {
			this.provisioningUsername = provisioningUsername;
		}

		public String getProvisioningPassword() {
			return provisioningPassword;
		}

		public void setProvisioningPassword(String provisioningPassword) {
			this.provisioningPassword = provisioningPassword;
		}

		public String getDatabasePrefix() {
			return databasePrefix;
		}

		public void setDatabasePrefix(String databasePrefix) {
			this.databasePrefix = databasePrefix;
		}

		public String getDatabaseUserHost() {
			return databaseUserHost;
		}

		public void setDatabaseUserHost(String databaseUserHost) {
			this.databaseUserHost = databaseUserHost;
		}
	}
}
