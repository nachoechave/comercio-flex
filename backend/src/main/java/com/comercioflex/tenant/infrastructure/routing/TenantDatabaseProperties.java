package com.comercioflex.tenant.infrastructure.routing;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.database")
public class TenantDatabaseProperties {

	private boolean tenantMigrationEnabled;
	private String migrationUsername;
	private String migrationPassword;
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

	public Map<String, ConnectionDetails> getTenantConnections() {
		return tenantConnections;
	}

	public void setTenantConnections(Map<String, ConnectionDetails> tenantConnections) {
		this.tenantConnections = tenantConnections;
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
}
