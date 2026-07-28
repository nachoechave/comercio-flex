CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_public_id UNIQUE (public_id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_categories_status_name ON categories (status, name);
