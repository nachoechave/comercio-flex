-- Existing catalog products remain sold by whole units.
ALTER TABLE products
    ADD COLUMN sale_unit VARCHAR(10) NOT NULL DEFAULT 'UNIT',
    ADD COLUMN sale_minimum DECIMAL(15,3) NOT NULL DEFAULT 1.000,
    ADD COLUMN sale_step DECIMAL(15,3) NOT NULL DEFAULT 1.000,
    ADD COLUMN sale_maximum DECIMAL(15,3) NOT NULL DEFAULT 99.000;

ALTER TABLE products
    ADD CONSTRAINT ck_products_sale_unit CHECK (sale_unit IN ('UNIT', 'KG')),
    ADD CONSTRAINT ck_products_sale_quantities CHECK (
        sale_minimum > 0 AND sale_step > 0 AND sale_maximum >= sale_minimum
    );
