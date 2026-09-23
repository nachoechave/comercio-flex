CREATE TABLE shipping_settings (
 id TINYINT PRIMARY KEY,
 free_shipping_threshold DECIMAL(15,2) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK (id = 1),
 CHECK (free_shipping_threshold IS NULL OR free_shipping_threshold >= 0)
);
INSERT INTO shipping_settings(id) VALUES (1);
CREATE TABLE shipping_methods (
 id CHAR(36) PRIMARY KEY,
 name VARCHAR(160) NOT NULL,
 description VARCHAR(1000) NULL,
 type VARCHAR(30) NOT NULL,
 price DECIMAL(15,2) NOT NULL,
 active BOOLEAN NOT NULL,
 pickup_address VARCHAR(500) NULL,
 instructions VARCHAR(1000) NULL,
 CHECK (type IN ('PICKUP','FIXED_RATE','LOCATION_RATE','POSTAL_CODE_RATE')),
 CHECK (price >= 0)
);
CREATE TABLE shipping_rules (
 method_id CHAR(36) NOT NULL,
 destination VARCHAR(160) NOT NULL,
 price DECIMAL(15,2) NOT NULL,
 PRIMARY KEY (method_id, destination),
 FOREIGN KEY (method_id) REFERENCES shipping_methods(id) ON DELETE CASCADE,
 CHECK (price >= 0)
);
-- Preserve the existing pickup experience until the merchant configures shipping.
INSERT INTO shipping_methods(id,name,type,price,active,pickup_address,instructions)
 SELECT UUID(), 'Retiro en el comercio', 'PICKUP', 0, TRUE,
 (SELECT pickup_address FROM store_settings LIMIT 1),
 (SELECT pickup_instructions FROM store_settings LIMIT 1);
ALTER TABLE orders
 DROP CHECK ck_orders_fulfillment,
 ADD CONSTRAINT ck_orders_fulfillment CHECK (fulfillment_type IN ('PICKUP','SHIPPING')),
 ADD shipping_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
 ADD shipping_snapshot JSON NULL,
 ADD total DECIMAL(15,2) GENERATED ALWAYS AS (subtotal + shipping_amount) STORED,
 ADD CONSTRAINT ck_orders_shipping_amount CHECK (shipping_amount >= 0);
CREATE TABLE shipments (
 id CHAR(36) PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE,
 provider VARCHAR(80) NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 carrier_name VARCHAR(160) NULL,
 tracking_number VARCHAR(160) NULL,
 tracking_url VARCHAR(1000) NULL,
 shipping_cost DECIMAL(15,2) NOT NULL,
 notes VARCHAR(1000) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 shipped_at TIMESTAMP(6) NULL,
 delivered_at TIMESTAMP(6) NULL,
 version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (order_id) REFERENCES orders(id),
 CHECK (status IN ('PENDING','PREPARING','SHIPPED','DELIVERED','CANCELLED')),
 CHECK (shipping_cost >= 0)
);

