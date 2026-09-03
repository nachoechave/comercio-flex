package com.comercioflex.tenant.infrastructure.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_domains")
class TenantDomainEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "tenant_id", nullable = false)
        private TenantEntity tenant;

        @Column(nullable = false, unique = true, length = 253)
        private String hostname;

        @Column(name = "primary_domain", nullable = false)
        private boolean primaryDomain;

        @Column(nullable = false)
        private boolean verified;

        protected TenantDomainEntity() {
        }
}