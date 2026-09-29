CREATE TABLE store_branches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    address VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_store_branches PRIMARY KEY (id),
    CONSTRAINT uk_store_branches_public_id UNIQUE (public_id),
    CONSTRAINT uk_store_branches_name UNIQUE (name)
);

INSERT INTO store_branches (public_id, name, active, is_default)
VALUES (UUID_TO_BIN(UUID()), 'Principal', TRUE, TRUE);

CREATE TABLE branch_inventory_balances (
    branch_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity DECIMAL(15,3) NOT NULL DEFAULT 0.000,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_branch_inventory_balances PRIMARY KEY (branch_id, variant_id),
    CONSTRAINT fk_branch_inventory_balances_branch FOREIGN KEY (branch_id)
        REFERENCES store_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_branch_inventory_balances_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT ck_branch_inventory_balances_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_branch_inventory_balances_version CHECK (version >= 0)
);

INSERT INTO branch_inventory_balances (branch_id, variant_id, quantity, version)
SELECT branch.id, balance.variant_id, balance.quantity, balance.version
FROM inventory_balances balance
JOIN store_branches branch ON branch.is_default = TRUE;

ALTER TABLE inventory_movements
    ADD COLUMN branch_id BIGINT NULL AFTER variant_id,
    ADD CONSTRAINT fk_inventory_movements_branch FOREIGN KEY (branch_id)
        REFERENCES store_branches (id) ON DELETE RESTRICT;

UPDATE inventory_movements movement
JOIN store_branches branch ON branch.is_default = TRUE
SET movement.branch_id = branch.id
WHERE movement.branch_id IS NULL;

ALTER TABLE inventory_movements
    MODIFY branch_id BIGINT NOT NULL;

CREATE TRIGGER trg_inventory_movements_default_branch
BEFORE INSERT ON inventory_movements
FOR EACH ROW
SET NEW.branch_id = COALESCE(
    NEW.branch_id,
    (SELECT id FROM store_branches WHERE is_default = TRUE LIMIT 1)
);

CREATE INDEX ix_branch_inventory_variant
    ON branch_inventory_balances (variant_id, branch_id);

CREATE INDEX ix_inventory_movements_branch_created
    ON inventory_movements (branch_id, created_at DESC, id DESC);
