CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    product_id BIGINT NOT NULL,
    display_storage_key VARCHAR(512) NOT NULL,
    thumbnail_storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    display_byte_size BIGINT NOT NULL,
    thumbnail_byte_size BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    alt_text VARCHAR(180) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT uk_product_images_public_id UNIQUE (public_id),
    CONSTRAINT uk_product_images_product UNIQUE (product_id),
    CONSTRAINT uk_product_images_display_key UNIQUE (display_storage_key),
    CONSTRAINT uk_product_images_thumbnail_key UNIQUE (thumbnail_storage_key),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_images_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_product_images_sizes
        CHECK (display_byte_size > 0 AND thumbnail_byte_size > 0),
    CONSTRAINT ck_product_images_dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_product_images_alt_text
        CHECK (CHAR_LENGTH(TRIM(alt_text)) BETWEEN 1 AND 180),
    CONSTRAINT ck_product_images_sha256
        CHECK (CHAR_LENGTH(sha256) = 64),
    CONSTRAINT ck_product_images_version CHECK (version >= 0)
);

CREATE INDEX ix_product_images_product_public
    ON product_images (product_id, public_id);
