ALTER TABLE quantity_promotions
    MODIFY product_id BIGINT NULL,
    ADD COLUMN scope_type VARCHAR(16) NOT NULL DEFAULT 'PRODUCT' AFTER public_id,
    ADD COLUMN category_id BIGINT NULL AFTER product_id,
    ADD KEY ix_quantity_promotions_category_active (category_id, active, starts_at, ends_at),
    ADD CONSTRAINT fk_quantity_promotions_category
      FOREIGN KEY (category_id) REFERENCES categories(id),
    ADD CONSTRAINT ck_quantity_promotions_scope
      CHECK (scope_type IN ('PRODUCT', 'PRODUCTS', 'CATEGORY'));

CREATE TABLE quantity_promotion_products (
    promotion_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (promotion_id, product_id),
    KEY ix_quantity_promotion_products_product (product_id),
    CONSTRAINT fk_quantity_promotion_products_promotion
      FOREIGN KEY (promotion_id) REFERENCES quantity_promotions(id) ON DELETE CASCADE,
    CONSTRAINT fk_quantity_promotion_products_product
      FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO quantity_promotion_products (promotion_id, product_id)
SELECT id, product_id
FROM quantity_promotions
WHERE product_id IS NOT NULL;

ALTER TABLE quantity_promotions
    DROP FOREIGN KEY fk_quantity_promotions_product,
    ADD CONSTRAINT fk_quantity_promotions_product
      FOREIGN KEY (product_id) REFERENCES products(id);
