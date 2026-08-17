ALTER TABLE store_settings
    ADD COLUMN primary_color CHAR(7) NOT NULL DEFAULT '#6D3CE7' AFTER brand_theme,
    ADD COLUMN secondary_color CHAR(7) NOT NULL DEFAULT '#2A1B4D' AFTER primary_color,
    ADD COLUMN background_color CHAR(7) NOT NULL DEFAULT '#F7F5FB' AFTER secondary_color,
    ADD COLUMN text_color CHAR(7) NOT NULL DEFAULT '#211A2D' AFTER background_color,
    ADD COLUMN brand_font ENUM('SYSTEM', 'SANS', 'SERIF') NOT NULL DEFAULT 'SYSTEM' AFTER text_color,
    ADD COLUMN hero_title VARCHAR(160) NULL AFTER brand_font,
    ADD COLUMN hero_subtitle VARCHAR(300) NULL AFTER hero_title,
    ADD COLUMN storefront_template ENUM('CLASSIC', 'MODERN', 'MINIMAL')
        NOT NULL DEFAULT 'CLASSIC' AFTER hero_subtitle,
    ADD COLUMN logo_storage_key VARCHAR(500) NULL AFTER storefront_template,
    ADD COLUMN logo_content_type VARCHAR(100) NULL AFTER logo_storage_key,
    ADD COLUMN logo_etag CHAR(64) NULL AFTER logo_content_type,
    ADD COLUMN favicon_storage_key VARCHAR(500) NULL AFTER logo_etag,
    ADD COLUMN favicon_content_type VARCHAR(100) NULL AFTER favicon_storage_key,
    ADD COLUMN favicon_etag CHAR(64) NULL AFTER favicon_content_type,
    ADD COLUMN hero_storage_key VARCHAR(500) NULL AFTER favicon_etag,
    ADD COLUMN hero_content_type VARCHAR(100) NULL AFTER hero_storage_key,
    ADD COLUMN hero_etag CHAR(64) NULL AFTER hero_content_type,
    ADD CONSTRAINT ck_store_settings_primary_color CHECK (primary_color REGEXP '^#[0-9A-F]{6}$'),
    ADD CONSTRAINT ck_store_settings_secondary_color CHECK (secondary_color REGEXP '^#[0-9A-F]{6}$'),
    ADD CONSTRAINT ck_store_settings_background_color CHECK (background_color REGEXP '^#[0-9A-F]{6}$'),
    ADD CONSTRAINT ck_store_settings_text_color CHECK (text_color REGEXP '^#[0-9A-F]{6}$');

UPDATE store_settings
SET primary_color = CASE brand_theme
        WHEN 'BURGUNDY' THEN '#8B2F45'
        WHEN 'FOREST' THEN '#276749'
        WHEN 'NAVY' THEN '#244A7C'
        ELSE '#6D3CE7'
    END;
