ALTER TABLE store_settings
    ADD COLUMN low_stock_threshold DECIMAL(15,3) NOT NULL DEFAULT 5.000
        AFTER timezone,
    ADD CONSTRAINT ck_store_settings_low_stock_threshold
        CHECK (low_stock_threshold >= 0);

CREATE INDEX ix_order_status_history_status_created
    ON order_status_history (new_status, created_at, order_id);
