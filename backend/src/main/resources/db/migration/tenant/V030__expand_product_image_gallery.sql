-- NULL positions are used only transiently inside locked reorder transactions to swap unique slots.
-- Existing images remain in place, with the same public IDs and storage objects.
ALTER TABLE product_images
    ADD COLUMN position INT NULL DEFAULT 0,
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN primary_product_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN is_primary THEN product_id ELSE NULL END) STORED,
    ADD CONSTRAINT uk_product_images_primary UNIQUE (primary_product_id),
    ADD CONSTRAINT ck_product_images_position CHECK (position BETWEEN 0 AND 5);

-- Create the replacement FK-supporting index before removing the one-image constraint.
CREATE UNIQUE INDEX uk_product_images_position ON product_images (product_id, position);
ALTER TABLE product_images DROP INDEX uk_product_images_product;
