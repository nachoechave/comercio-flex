ALTER TABLE store_settings
    MODIFY COLUMN storefront_template
        ENUM('CLASSIC', 'MODERN', 'MINIMAL', 'FASHION', 'FRESH', 'CATALOG')
        NOT NULL DEFAULT 'CLASSIC';

UPDATE store_settings
SET storefront_template = CASE storefront_template
    WHEN 'MODERN' THEN 'FASHION'
    ELSE 'CATALOG'
END;

ALTER TABLE store_settings
    MODIFY COLUMN storefront_template ENUM('FASHION', 'FRESH', 'CATALOG')
        NOT NULL DEFAULT 'CATALOG';
