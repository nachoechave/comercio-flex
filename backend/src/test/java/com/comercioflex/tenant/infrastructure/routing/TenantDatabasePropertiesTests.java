package com.comercioflex.tenant.infrastructure.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class TenantDatabasePropertiesTests {

	@Test
	void ignoresConnectionsWithoutAnyConfiguredCredential() {
		TenantDatabaseProperties properties = new TenantDatabaseProperties();
		var connections = new LinkedHashMap<String, TenantDatabaseProperties.ConnectionDetails>();
		connections.put("tenant-a", new TenantDatabaseProperties.ConnectionDetails());
		properties.setTenantConnections(connections);

		assertThat(properties.configuredTenantConnections()).isEmpty();
	}

	@Test
	void returnsCompleteConnections() {
		TenantDatabaseProperties properties = new TenantDatabaseProperties();
		var connections = new LinkedHashMap<String, TenantDatabaseProperties.ConnectionDetails>();
		var details = new TenantDatabaseProperties.ConnectionDetails();
		details.setUrl("jdbc:mysql://mysql.example:3306/comercio_flex_tenant_a");
		details.setUsername("tenant_app");
		details.setPassword("secret");
		connections.put("tenant-a", details);
		properties.setTenantConnections(connections);

		assertThat(properties.configuredTenantConnections())
			.containsOnlyKeys("tenant-a")
			.containsEntry("tenant-a", details);
	}

	@Test
	void rejectsPartiallyConfiguredConnections() {
		TenantDatabaseProperties properties = new TenantDatabaseProperties();
		var connections = new LinkedHashMap<String, TenantDatabaseProperties.ConnectionDetails>();
		var details = new TenantDatabaseProperties.ConnectionDetails();
		details.setUrl("jdbc:mysql://mysql.example:3306/comercio_flex_tenant_a");
		details.setUsername("tenant_app");
		connections.put("tenant-a", details);
		properties.setTenantConnections(connections);

		assertThatThrownBy(properties::configuredTenantConnections)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("tenant-a");
	}
}
