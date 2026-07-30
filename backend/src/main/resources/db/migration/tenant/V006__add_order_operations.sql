ALTER TABLE orders
    DROP CHECK ck_orders_status,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status,
    ADD CONSTRAINT ck_orders_status CHECK (
        status IN (
            'PENDING_CONFIRMATION',
            'CONFIRMED',
            'READY_FOR_PICKUP',
            'COMPLETED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    );

ALTER TABLE inventory_movements
    DROP CHECK ck_inventory_movements_reason,
    ADD COLUMN order_id BIGINT NULL AFTER variant_id,
    ADD CONSTRAINT fk_inventory_movements_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_inventory_movements_reason CHECK (
        reason IN (
            'RECEIPT',
            'CORRECTION',
            'DAMAGE',
            'RETURN',
            'OTHER',
            'ORDER_CONFIRMED',
            'ORDER_CANCELLED'
        )
    );

CREATE INDEX ix_inventory_movements_order
    ON inventory_movements (order_id, created_at DESC, id DESC);

CREATE TABLE order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    order_id BIGINT NOT NULL,
    idempotency_key BINARY(16) NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    note VARCHAR(500) NULL,
    actor_public_id BINARY(16) NULL,
    actor_display_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT uk_order_status_history_public_id UNIQUE (public_id),
    CONSTRAINT uk_order_status_history_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_status_history_previous CHECK (
        previous_status IS NULL OR previous_status IN (
            'PENDING_CONFIRMATION',
            'CONFIRMED',
            'READY_FOR_PICKUP',
            'COMPLETED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    ),
    CONSTRAINT ck_order_status_history_new CHECK (
        new_status IN (
            'PENDING_CONFIRMATION',
            'CONFIRMED',
            'READY_FOR_PICKUP',
            'COMPLETED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    )
);

CREATE INDEX ix_order_status_history_order_created
    ON order_status_history (order_id, created_at, id);

INSERT INTO order_status_history (
    public_id,
    order_id,
    previous_status,
    new_status,
    actor_display_name,
    created_at
)
SELECT
    UUID_TO_BIN(UUID()),
    id,
    NULL,
    status,
    'Sistema',
    created_at
FROM orders;
