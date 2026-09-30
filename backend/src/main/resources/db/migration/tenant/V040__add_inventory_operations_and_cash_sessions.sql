CREATE TABLE inventory_transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    from_branch_id BIGINT NOT NULL,
    to_branch_id BIGINT NOT NULL,
    actor_public_id BINARY(16) NOT NULL,
    actor_display_name VARCHAR(160) NOT NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_transfers PRIMARY KEY (id),
    CONSTRAINT uk_inventory_transfers_public_id UNIQUE (public_id),
    CONSTRAINT uk_inventory_transfers_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_inventory_transfers_from_branch FOREIGN KEY (from_branch_id)
        REFERENCES store_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_transfers_to_branch FOREIGN KEY (to_branch_id)
        REFERENCES store_branches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_transfers_distinct_branches CHECK (from_branch_id <> to_branch_id)
);

CREATE INDEX ix_inventory_transfers_created
    ON inventory_transfers (created_at DESC, id DESC);
CREATE INDEX ix_inventory_transfers_from_created
    ON inventory_transfers (from_branch_id, created_at DESC, id DESC);
CREATE INDEX ix_inventory_transfers_to_created
    ON inventory_transfers (to_branch_id, created_at DESC, id DESC);

CREATE TABLE inventory_transfer_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    CONSTRAINT pk_inventory_transfer_items PRIMARY KEY (id),
    CONSTRAINT uk_inventory_transfer_items_variant UNIQUE (transfer_id, variant_id),
    CONSTRAINT fk_inventory_transfer_items_transfer FOREIGN KEY (transfer_id)
        REFERENCES inventory_transfers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_transfer_items_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_transfer_items_quantity CHECK (quantity > 0)
);

ALTER TABLE inventory_movements
    DROP CHECK ck_inventory_movements_reason,
    ADD COLUMN transfer_id BIGINT NULL AFTER pos_sale_id,
    ADD CONSTRAINT fk_inventory_movements_transfer FOREIGN KEY (transfer_id)
        REFERENCES inventory_transfers (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_inventory_movements_reason CHECK (
        reason IN (
            'RECEIPT',
            'CORRECTION',
            'DAMAGE',
            'RETURN',
            'OTHER',
            'ORDER_CONFIRMED',
            'ORDER_CANCELLED',
            'LOCAL_SALE',
            'TRANSFER_OUT',
            'TRANSFER_IN'
        )
    );

CREATE INDEX ix_inventory_movements_transfer
    ON inventory_movements (transfer_id, created_at DESC, id DESC);

CREATE TABLE cash_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    branch_id BIGINT NOT NULL,
    opened_by_public_id BINARY(16) NOT NULL,
    opened_by_display_name VARCHAR(160) NOT NULL,
    opening_amount DECIMAL(15,2) NOT NULL,
    opened_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_by_public_id BINARY(16) NULL,
    closed_by_display_name VARCHAR(160) NULL,
    closing_amount DECIMAL(15,2) NULL,
    expected_cash DECIMAL(15,2) NULL,
    difference_amount DECIMAL(15,2) NULL,
    closed_at TIMESTAMP(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT pk_cash_sessions PRIMARY KEY (id),
    CONSTRAINT uk_cash_sessions_public_id UNIQUE (public_id),
    CONSTRAINT fk_cash_sessions_branch FOREIGN KEY (branch_id)
        REFERENCES store_branches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_sessions_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_cash_sessions_opening_amount CHECK (opening_amount >= 0),
    CONSTRAINT ck_cash_sessions_closing_amount CHECK (closing_amount IS NULL OR closing_amount >= 0)
);

CREATE INDEX ix_cash_sessions_branch_status_opened
    ON cash_sessions (branch_id, status, opened_at DESC, id DESC);
