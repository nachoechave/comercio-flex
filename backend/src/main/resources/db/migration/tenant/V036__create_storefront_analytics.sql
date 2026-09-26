CREATE TABLE storefront_analytics_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL,
    visitor_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    path VARCHAR(512) NOT NULL,
    source VARCHAR(120) NOT NULL DEFAULT 'Directo',
    medium VARCHAR(80) NULL,
    product_public_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_storefront_analytics_type_created (event_type, created_at),
    INDEX idx_storefront_analytics_session_created (session_id, created_at),
    INDEX idx_storefront_analytics_visitor_created (visitor_id, created_at),
    INDEX idx_storefront_analytics_product_created (product_public_id, created_at)
);
