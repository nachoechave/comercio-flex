CREATE TABLE pos_sales (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    branch_id BIGINT NOT NULL,
    seller_public_id BINARY(16) NOT NULL,
    seller_display_name VARCHAR(160) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_pos_sales PRIMARY KEY (id),
    CONSTRAINT uk_pos_sales_public_id UNIQUE (public_id),
    CONSTRAINT uk_pos_sales_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_pos_sales_branch FOREIGN KEY (branch_id)
        REFERENCES store_branches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_pos_sales_payment_method CHECK (
        payment_method IN ('CASH', 'BANK_TRANSFER', 'CARD', 'OTHER')
    ),
    CONSTRAINT ck_pos_sales_subtotal CHECK (subtotal > 0)
);

CREATE INDEX ix_pos_sales_created
    ON pos_sales (created_at DESC, id DESC);
CREATE INDEX ix_pos_sales_branch_created
    ON pos_sales (branch_id, created_at DESC, id DESC);

CREATE TABLE pos_sale_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sale_id BIGINT NOT NULL,
    product_public_id BINARY(16) NOT NULL,
    variant_id BIGINT NOT NULL,
    variant_public_id BINARY(16) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    sku_snapshot VARCHAR(64) NOT NULL,
    size_snapshot VARCHAR(60) NOT NULL DEFAULT '',
    color_snapshot VARCHAR(60) NOT NULL DEFAULT '',
    unit_price DECIMAL(15,2) NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    CONSTRAINT pk_pos_sale_items PRIMARY KEY (id),
    CONSTRAINT uk_pos_sale_items_variant UNIQUE (sale_id, variant_id),
    CONSTRAINT fk_pos_sale_items_sale FOREIGN KEY (sale_id)
        REFERENCES pos_sales (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pos_sale_items_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_pos_sale_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_pos_sale_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_pos_sale_items_total CHECK (line_total > 0)
);

ALTER TABLE inventory_movements
    DROP CHECK ck_inventory_movements_reason,
    ADD COLUMN pos_sale_id BIGINT NULL AFTER order_id,
    ADD CONSTRAINT fk_inventory_movements_pos_sale FOREIGN KEY (pos_sale_id)
        REFERENCES pos_sales (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_inventory_movements_reason CHECK (
        reason IN (
            'RECEIPT',
            'CORRECTION',
            'DAMAGE',
            'RETURN',
            'OTHER',
            'ORDER_CONFIRMED',
            'ORDER_CANCELLED',
            'LOCAL_SALE'
        )
    );

CREATE INDEX ix_inventory_movements_pos_sale
    ON inventory_movements (pos_sale_id, created_at DESC, id DESC);
