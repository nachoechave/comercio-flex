ALTER TABLE product_variants
    DROP INDEX uk_product_variants_options,
    ADD COLUMN option_signature CHAR(64) NULL AFTER color_value;

CREATE TABLE product_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    normalized_name VARCHAR(40) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_product_options PRIMARY KEY (id),
    CONSTRAINT uk_product_options_public_id UNIQUE (public_id),
    CONSTRAINT uk_product_options_name UNIQUE (product_id, normalized_name),
    CONSTRAINT uk_product_options_position UNIQUE (product_id, position),
    CONSTRAINT fk_product_options_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_options_position CHECK (position BETWEEN 1 AND 5)
);

CREATE TABLE product_option_values (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    option_id BIGINT NOT NULL,
    value VARCHAR(60) NOT NULL,
    normalized_value VARCHAR(60) NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_product_option_values PRIMARY KEY (id),
    CONSTRAINT uk_product_option_values_public_id UNIQUE (public_id),
    CONSTRAINT uk_product_option_values_value UNIQUE (option_id, normalized_value),
    CONSTRAINT uk_product_option_values_position UNIQUE (option_id, position),
    CONSTRAINT fk_product_option_values_option FOREIGN KEY (option_id)
        REFERENCES product_options (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_option_values_position CHECK (position BETWEEN 1 AND 100)
);

CREATE TABLE product_variant_option_values (
    variant_id BIGINT NOT NULL,
    option_value_id BIGINT NOT NULL,
    CONSTRAINT pk_product_variant_option_values PRIMARY KEY (variant_id, option_value_id),
    CONSTRAINT fk_product_variant_option_values_variant FOREIGN KEY (variant_id)
        REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_variant_option_values_value FOREIGN KEY (option_value_id)
        REFERENCES product_option_values (id) ON DELETE RESTRICT
);

CREATE INDEX ix_product_variant_option_values_value
    ON product_variant_option_values (option_value_id, variant_id);

INSERT INTO product_options (
    public_id, product_id, name, normalized_name, position
)
SELECT UUID_TO_BIN(UUID()), variant.product_id, 'Talle', 'talle', 1
FROM product_variants variant
WHERE variant.size_value <> ''
GROUP BY variant.product_id;

INSERT INTO product_options (
    public_id, product_id, name, normalized_name, position
)
SELECT UUID_TO_BIN(UUID()), variant.product_id, 'Color', 'color',
       CASE WHEN EXISTS (
           SELECT 1 FROM product_options existing
           WHERE existing.product_id = variant.product_id
       ) THEN 2 ELSE 1 END
FROM product_variants variant
WHERE variant.color_value <> ''
GROUP BY variant.product_id;

INSERT INTO product_option_values (
    public_id, option_id, value, normalized_value, position
)
SELECT UUID_TO_BIN(UUID()), product_option.id, variant.size_value,
       LOWER(variant.size_value),
       ROW_NUMBER() OVER (
           PARTITION BY product_option.id ORDER BY variant.size_value
       )
FROM product_variants variant
JOIN product_options product_option
    ON product_option.product_id = variant.product_id
   AND product_option.normalized_name = 'talle'
WHERE variant.size_value <> ''
GROUP BY product_option.id, variant.size_value;

INSERT INTO product_option_values (
    public_id, option_id, value, normalized_value, position
)
SELECT UUID_TO_BIN(UUID()), product_option.id, variant.color_value,
       LOWER(variant.color_value),
       ROW_NUMBER() OVER (
           PARTITION BY product_option.id ORDER BY variant.color_value
       )
FROM product_variants variant
JOIN product_options product_option
    ON product_option.product_id = variant.product_id
   AND product_option.normalized_name = 'color'
WHERE variant.color_value <> ''
GROUP BY product_option.id, variant.color_value;

INSERT INTO product_variant_option_values (variant_id, option_value_id)
SELECT variant.id, option_value.id
FROM product_variants variant
JOIN product_options product_option
    ON product_option.product_id = variant.product_id
   AND product_option.normalized_name = 'talle'
JOIN product_option_values option_value
    ON option_value.option_id = product_option.id
   AND option_value.normalized_value = LOWER(variant.size_value)
WHERE variant.size_value <> '';

INSERT INTO product_variant_option_values (variant_id, option_value_id)
SELECT variant.id, option_value.id
FROM product_variants variant
JOIN product_options product_option
    ON product_option.product_id = variant.product_id
   AND product_option.normalized_name = 'color'
JOIN product_option_values option_value
    ON option_value.option_id = product_option.id
   AND option_value.normalized_value = LOWER(variant.color_value)
WHERE variant.color_value <> '';

UPDATE product_variants
SET option_signature = SHA2(CONCAT(
    IF(color_value = '', '', CONCAT('color=', LOWER(color_value))),
    IF(color_value = '' OR size_value = '', '', CHAR(31)),
    IF(size_value = '', '', CONCAT('talle=', LOWER(size_value)))
), 256);

ALTER TABLE product_variants
    MODIFY option_signature CHAR(64) NOT NULL,
    ADD CONSTRAINT uk_product_variants_option_signature
        UNIQUE (product_id, option_signature);

ALTER TABLE order_items
    ADD COLUMN options_snapshot JSON NULL AFTER color_snapshot;

UPDATE order_items
SET options_snapshot = CASE
    WHEN size_snapshot <> '' AND color_snapshot <> '' THEN JSON_ARRAY(
        JSON_OBJECT('name', 'Talle', 'value', size_snapshot),
        JSON_OBJECT('name', 'Color', 'value', color_snapshot)
    )
    WHEN size_snapshot <> '' THEN JSON_ARRAY(
        JSON_OBJECT('name', 'Talle', 'value', size_snapshot)
    )
    WHEN color_snapshot <> '' THEN JSON_ARRAY(
        JSON_OBJECT('name', 'Color', 'value', color_snapshot)
    )
    ELSE JSON_ARRAY()
END;

ALTER TABLE order_items
    MODIFY options_snapshot JSON NOT NULL;
