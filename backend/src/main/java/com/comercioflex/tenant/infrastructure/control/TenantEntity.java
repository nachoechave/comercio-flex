package com.comercioflex.tenant.infrastructure.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
class TenantEntity {

	@Id
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String slug;

	@Column(name = "database_key", nullable = false, unique = true, length = 100)
	private String databaseKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TenantStatus status;

	protected TenantEntity() {
	}
}
