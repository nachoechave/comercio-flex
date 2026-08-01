package com.comercioflex.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.infrastructure.routing.TenantDataSourceRegistry;
import com.comercioflex.tenant.infrastructure.routing.TenantDatabaseProperties;

@Configuration
@EnableConfigurationProperties(TenantDatabaseProperties.class)
public class TenantDataAccessConfig {

	@Bean("controlJdbcTemplate")
	JdbcTemplate controlJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean("controlTransactionTemplate")
	TransactionTemplate controlTransactionTemplate(
			@Qualifier("dataSource") DataSource dataSource) {
		return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	@Bean
	TenantDataSourceRegistry tenantDataSourceRegistry(
			TenantContext tenantContext,
			TenantDatabaseProperties properties) {
		return new TenantDataSourceRegistry(tenantContext, properties);
	}

	@Bean("tenantJdbcTemplate")
	JdbcTemplate tenantJdbcTemplate(TenantDataSourceRegistry registry) {
		return new JdbcTemplate(registry.routingDataSource());
	}

	@Bean("tenantTransactionTemplate")
	TransactionTemplate tenantTransactionTemplate(TenantDataSourceRegistry registry) {
		DataSourceTransactionManager transactionManager =
			new DataSourceTransactionManager(registry.routingDataSource());
		return new TransactionTemplate(transactionManager);
	}
}
