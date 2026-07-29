CREATE TABLE inventory_balances (
    variant_id BIGINT NOT NULL,
    quantity DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_balances PRIMARY KEY (variant_id),
    CONSTRAINT fk_inventory_balances_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_balances_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_balances_version CHECK (version >= 0)
);

CREATE TABLE inventory_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    variant_id BIGINT NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    delta_quantity DECIMAL(15,3) NOT NULL,
    quantity_before DECIMAL(15,3) NOT NULL,
    quantity_after DECIMAL(15,3) NOT NULL,
    balance_version BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    actor_public_id BINARY(16) NOT NULL,
    actor_display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT uk_inventory_movements_public_id UNIQUE (public_id),
    CONSTRAINT uk_inventory_movements_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_inventory_movements_balance_version
        UNIQUE (variant_id, balance_version),
    CONSTRAINT fk_inventory_movements_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_movements_direction
        CHECK (direction IN ('INCREASE', 'DECREASE')),
    CONSTRAINT ck_inventory_movements_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_movements_balance_version CHECK (balance_version > 0),
    CONSTRAINT ck_inventory_movements_quantities
        CHECK (
            quantity_before >= 0
            AND quantity_after >= 0
            AND (
                (direction = 'INCREASE'
                    AND delta_quantity = quantity
                    AND quantity_after = quantity_before + quantity)
                OR
                (direction = 'DECREASE'
                    AND delta_quantity = -quantity
                    AND quantity_after = quantity_before - quantity)
            )
        ),
    CONSTRAINT ck_inventory_movements_reason
        CHECK (reason IN ('RECEIPT', 'CORRECTION', 'DAMAGE', 'RETURN', 'OTHER'))
);

CREATE INDEX ix_inventory_movements_variant_created
    ON inventory_movements (variant_id, created_at DESC, id DESC);
