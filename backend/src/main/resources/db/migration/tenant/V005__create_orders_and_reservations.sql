CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    idempotency_key BINARY(16) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    lookup_token_hash BINARY(32) NOT NULL,
    status VARCHAR(30) NOT NULL,
    fulfillment_type VARCHAR(20) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    customer_phone VARCHAR(40) NOT NULL,
    customer_email VARCHAR(254) NULL,
    customer_notes VARCHAR(1000) NULL,
    currency_code CHAR(3) NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    reservation_expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_public_id UNIQUE (public_id),
    CONSTRAINT uk_orders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING_CONFIRMATION', 'EXPIRED')),
    CONSTRAINT ck_orders_fulfillment CHECK (fulfillment_type = 'PICKUP'),
    CONSTRAINT ck_orders_subtotal CHECK (subtotal > 0)
);

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_public_id BINARY(16) NOT NULL,
    variant_id BIGINT NOT NULL,
    variant_public_id BINARY(16) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    sku_snapshot VARCHAR(64) NOT NULL,
    size_snapshot VARCHAR(60) NOT NULL DEFAULT '',
    color_snapshot VARCHAR(60) NOT NULL DEFAULT '',
    unit_code VARCHAR(20) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT uk_order_items_variant UNIQUE (order_id, variant_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_items_unit CHECK (unit_code = 'UNIT'),
    CONSTRAINT ck_order_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_total CHECK (line_total > 0)
);

CREATE TABLE inventory_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    order_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_reservations PRIMARY KEY (id),
    CONSTRAINT uk_inventory_reservations_public_id UNIQUE (public_id),
    CONSTRAINT uk_inventory_reservations_order_variant
        UNIQUE (order_id, variant_id),
    CONSTRAINT fk_inventory_reservations_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reservations_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inventory_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservations_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX ix_orders_status_created
    ON orders (status, created_at DESC, id DESC);
CREATE INDEX ix_inventory_reservations_variant_active
    ON inventory_reservations (variant_id, status, expires_at);
CREATE INDEX ix_inventory_reservations_expiration
    ON inventory_reservations (status, expires_at);
