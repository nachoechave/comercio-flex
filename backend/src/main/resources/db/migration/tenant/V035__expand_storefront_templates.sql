ALTER TABLE store_settings
    MODIFY COLUMN storefront_template ENUM(
        'FASHION',
        'FRESH',
        'CATALOG',
        'COAST',
        'MINIMAL',
        'LUXE',
        'URBAN',
        'EDITORIAL',
        'MARKET',
        'STUDIO',
        'BOLD'
    ) NOT NULL DEFAULT 'CATALOG';
