CREATE TABLE platform_monthly_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    tenant_id BIGINT NOT NULL,
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    amount DECIMAL(15,2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(40) NULL,
    paid_at TIMESTAMP(6) NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_monthly_payments_public_id (public_id),
    UNIQUE KEY uk_platform_monthly_payments_tenant_period (tenant_id, period_year, period_month),
    KEY ix_platform_monthly_payments_period_status (period_year, period_month, status),
    CONSTRAINT fk_platform_monthly_payments_tenant
      FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_platform_monthly_payments_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_platform_monthly_payments_status CHECK (status IN ('PENDING', 'PAID', 'BONIFIED')),
    CONSTRAINT ck_platform_monthly_payments_amount CHECK (amount IS NULL OR amount >= 0)
);
