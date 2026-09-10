CREATE TABLE radio_site_settings (
 id BIGINT NOT NULL PRIMARY KEY,
 hero_title VARCHAR(160) NULL,
 hero_subtitle VARCHAR(300) NULL,
 description VARCHAR(1000) NULL,
 youtube_url VARCHAR(500) NULL,
 instagram_url VARCHAR(500) NULL,
 x_url VARCHAR(500) NULL,
 whatsapp_url VARCHAR(500) NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_radio_site_settings_id CHECK (id = 1)
);
INSERT INTO radio_site_settings (id) VALUES (1);

CREATE TABLE radio_programs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 public_id BINARY(16) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 description VARCHAR(1000) NOT NULL,
 days VARCHAR(160) NOT NULL,
 schedule VARCHAR(120) NOT NULL,
 image_url VARCHAR(1000) NULL,
 hosts VARCHAR(500) NULL,
 display_order INT NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_radio_program_order CHECK (display_order >= 0),
 INDEX ix_radio_program_public (active, display_order, id)
);

CREATE TABLE radio_team_members (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 public_id BINARY(16) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 role_name VARCHAR(160) NOT NULL,
 bio VARCHAR(1000) NOT NULL,
 photo_url VARCHAR(1000) NULL,
 social_url VARCHAR(500) NULL,
 display_order INT NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_radio_team_order CHECK (display_order >= 0),
 INDEX ix_radio_team_public (active, display_order, id)
);

CREATE TABLE radio_sponsors (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 public_id BINARY(16) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 logo_url VARCHAR(1000) NULL,
 target_url VARCHAR(500) NULL,
 description VARCHAR(500) NULL,
 sponsor_level VARCHAR(30) NOT NULL DEFAULT 'SECONDARY',
 display_order INT NOT NULL DEFAULT 0,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT ck_radio_sponsor_level CHECK (sponsor_level IN ('PRIMARY','SECONDARY')),
 CONSTRAINT ck_radio_sponsor_order CHECK (display_order >= 0),
 INDEX ix_radio_sponsor_public (active, sponsor_level, display_order, id)
);
