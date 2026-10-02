ALTER TABLE orders
    ADD COLUMN fulfillment_branch_id BIGINT NULL AFTER public_id,
    ADD CONSTRAINT fk_orders_fulfillment_branch
        FOREIGN KEY (fulfillment_branch_id) REFERENCES store_branches(id),
    ADD INDEX idx_orders_fulfillment_branch (fulfillment_branch_id);

ALTER TABLE inventory_reservations
    ADD COLUMN branch_id BIGINT NULL,
    ADD CONSTRAINT fk_inventory_reservations_branch
        FOREIGN KEY (branch_id) REFERENCES store_branches(id),
    ADD INDEX idx_inventory_reservations_branch (branch_id);

-- Los registros históricos permanecen nullable. La capa de aplicación conserva
-- la compatibilidad resolviendo la sucursal Principal cuando branch_id es NULL.
