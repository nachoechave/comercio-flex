package com.comercioflex.platformadmin.infrastructure.mysql;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.comercioflex.platformadmin.application.PendingCompany;
import com.comercioflex.platformadmin.application.TenantProvisioner;
import com.comercioflex.tenant.infrastructure.routing.ManagedTenantConnectionFactory;
import com.comercioflex.tenant.infrastructure.routing.TenantDatabaseProperties;
import com.comercioflex.tenant.infrastructure.routing.TenantDataSourceRegistry;
import com.comercioflex.tenant.infrastructure.routing.TenantSchemaMigrator;

@Component
public class MySqlTenantProvisioner implements TenantProvisioner {

	private static final Pattern SAFE_ACCOUNT = Pattern.compile("[A-Za-z0-9_@.-]{1,64}");
	private static final Pattern SAFE_HOST = Pattern.compile("[%A-Za-z0-9_.:-]{1,255}");

	private final ManagedTenantConnectionFactory connectionFactory;
	private final TenantDatabaseProperties databaseProperties;
	private final TenantSchemaMigrator schemaMigrator;
	private final TenantDataSourceRegistry registry;

	public MySqlTenantProvisioner(
			ManagedTenantConnectionFactory connectionFactory,
			TenantDatabaseProperties databaseProperties,
			TenantSchemaMigrator schemaMigrator,
			TenantDataSourceRegistry registry) {
		this.connectionFactory = connectionFactory;
		this.databaseProperties = databaseProperties;
		this.schemaMigrator = schemaMigrator;
		this.registry = registry;
	}

	@Override
	public void provision(PendingCompany company) {
		connectionFactory.requireProvisioningEnabled();
		String targetUrl = connectionFactory.urlFor(company.databaseName());
		createDatabase(company.databaseName());
		schemaMigrator.migrate(targetUrl);
		initializeStore(company, targetUrl);
		registry.register(
			company.databaseKey(),
			connectionFactory.applicationDetails(company.databaseName()));
	}

	private void createDatabase(String databaseName) {
		TenantDatabaseProperties.ManagedConnections managed = connectionFactory.managed();
		try (var connection = DriverManager.getConnection(
				managed.getProvisioningUrl(),
				managed.getProvisioningUsername(),
				managed.getProvisioningPassword());
			Statement statement = connection.createStatement()) {
			statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + databaseName
				+ "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
			grantExactDatabasePermissions(statement, databaseName, managed);
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Could not create managed tenant database", exception);
		}
	}

	private void grantExactDatabasePermissions(
			Statement statement,
			String databaseName,
			TenantDatabaseProperties.ManagedConnections managed) throws SQLException {
		String applicationUser = safeAccount(managed.getApplicationUsername());
		String migrationUser = safeAccount(databaseProperties.getMigrationUsername());
		String host = safeHost(managed.getDatabaseUserHost());
		statement.executeUpdate("GRANT SELECT, INSERT, UPDATE, DELETE ON `"
			+ databaseName + "`.* TO '" + applicationUser + "'@'" + host + "'");
		statement.executeUpdate("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, "
			+ "INDEX, REFERENCES, CREATE VIEW, SHOW VIEW, TRIGGER ON `" + databaseName
			+ "`.* TO '" + migrationUser + "'@'" + host + "'");
	}

	private String safeAccount(String account) {
		if (account == null || !SAFE_ACCOUNT.matcher(account).matches()) {
			throw new IllegalStateException("Invalid managed database account name");
		}
		return account;
	}

	private String safeHost(String host) {
		if (host == null || !SAFE_HOST.matcher(host).matches()) {
			throw new IllegalStateException("Invalid managed database account host");
		}
		return host;
	}

	private void initializeStore(PendingCompany company, String targetUrl) {
		try (var connection = DriverManager.getConnection(
				targetUrl,
				databaseProperties.getMigrationUsername(),
				databaseProperties.getMigrationPassword());
			PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO store_settings (store_name, contact_phone, contact_email)
				SELECT ?, ?, ?
				WHERE NOT EXISTS (SELECT 1 FROM store_settings)
				""")) {
			statement.setString(1, company.name());
			statement.setString(2, company.administratorPhone());
			statement.setString(3, company.administratorEmail());
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Could not initialize managed tenant settings", exception);
		}
	}
}
