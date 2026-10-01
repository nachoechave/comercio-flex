CREATE TABLE quantity_promotions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    bundle_quantity INT NOT NULL,
    bundle_price DECIMAL(15,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP(6) NULL,
    ends_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quantity_promotions_public_id (public_id),
    KEY ix_quantity_promotions_product_active (product_id, active, starts_at, ends_at),
    CONSTRAINT fk_quantity_promotions_product
      FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_quantity_promotions_quantity CHECK (bundle_quantity BETWEEN 2 AND 99),
    CONSTRAINT ck_quantity_promotions_price CHECK (bundle_price > 0)
);
