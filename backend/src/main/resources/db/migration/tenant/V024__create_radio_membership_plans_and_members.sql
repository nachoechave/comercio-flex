CREATE TABLE membership_plans (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 public_id BINARY(16) NOT NULL UNIQUE,
 name VARCHAR(120) NOT NULL,
 description VARCHAR(2000) NOT NULL,
 price DECIMAL(12,2) NOT NULL,
 currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 benefits JSON NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 display_order INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_membership_plan_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
 CONSTRAINT ck_membership_plan_price CHECK (price >= 0),
 CONSTRAINT ck_membership_plan_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
 CONSTRAINT ck_membership_plan_benefits CHECK (JSON_TYPE(benefits) = 'ARRAY'),
 INDEX ix_membership_plan_listing (active, display_order)
);

CREATE TABLE paid_memberships (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 public_id BINARY(16) NOT NULL UNIQUE,
 -- Stable public UUID of platform_users in control; deliberately no cross-database FK.
 platform_user_id BINARY(16) NOT NULL UNIQUE,
 current_plan_id BIGINT NOT NULL,
 started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 cancelled_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_paid_membership_plan FOREIGN KEY (current_plan_id) REFERENCES membership_plans(id),
 INDEX ix_paid_membership_cancelled (cancelled_at)
);
