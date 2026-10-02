ALTER TABLE quantity_promotions
    DROP CHECK ck_quantity_promotions_scope,
    ADD CONSTRAINT ck_quantity_promotions_scope CHECK (scope_type IN ('PRODUCT', 'PRODUCTS', 'CATEGORY', 'COMBO'));
ALTER TABLE quantity_promotion_products
    ADD COLUMN group_number TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_promotion_group CHECK (group_number IN (1, 2));
