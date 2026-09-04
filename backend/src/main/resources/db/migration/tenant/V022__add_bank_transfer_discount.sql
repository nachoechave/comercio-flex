ALTER TABLE store_settings
    ADD COLUMN bank_transfer_discount_percentage DECIMAL(5,2)
        NOT NULL DEFAULT 0.00
        AFTER bank_transfer_enabled,
    ADD CONSTRAINT ck_store_settings_bank_transfer_discount
        CHECK (
            bank_transfer_discount_percentage >= 0.00
            AND bank_transfer_discount_percentage <= 50.00
        );