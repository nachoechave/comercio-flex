ALTER TABLE store_settings
    ADD COLUMN contact_phone VARCHAR(40) NULL AFTER low_stock_threshold,
    ADD COLUMN contact_email VARCHAR(254) NULL AFTER contact_phone,
    ADD COLUMN pickup_address VARCHAR(240) NULL AFTER contact_email,
    ADD COLUMN pickup_instructions VARCHAR(500) NULL AFTER pickup_address,
    ADD COLUMN brand_theme ENUM('VIOLET', 'BURGUNDY', 'FOREST', 'NAVY')
        NOT NULL DEFAULT 'VIOLET' AFTER pickup_instructions;
