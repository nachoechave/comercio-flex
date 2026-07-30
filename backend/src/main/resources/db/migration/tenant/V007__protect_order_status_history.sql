ALTER TABLE order_status_history
    ADD CONSTRAINT uk_order_status_history_order_status
        UNIQUE (order_id, new_status);
