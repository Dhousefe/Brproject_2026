-- =========================================================================
-- V1_0_8: Adiciona protecao de hardware (HWID Guard) e compras do Site/Web
-- =========================================================================

CREATE TABLE IF NOT EXISTS "accounts_hardware_guard" (
	"login" VARCHAR(45) NOT NULL,
	"credential_id" VARCHAR(255) NOT NULL,
	"public_key_der" TEXT NOT NULL,
	"algorithm" INT NOT NULL DEFAULT -7,
	"device_name" VARCHAR(120) NOT NULL DEFAULT 'Windows Hello (TPM 2.0)',
	"sign_count" BIGINT NOT NULL DEFAULT 0,
	"created_at" BIGINT NOT NULL DEFAULT 0,
	"last_used_at" BIGINT NOT NULL DEFAULT 0,
	PRIMARY KEY ("login", "credential_id"),
	KEY "idx_hw_guard_login" ("login")
);

CREATE TABLE IF NOT EXISTS "site_shop_purchases" (
	"id" INT NOT NULL ,
	"created_at" BIGINT NOT NULL,
	"account_name" VARCHAR(45) NOT NULL,
	"character_id" INT NOT NULL DEFAULT 0,
	"character_name" VARCHAR(35) NOT NULL,
	"item_key" VARCHAR(60) NOT NULL,
	"item_id" INT NOT NULL,
	"item_name" VARCHAR(100) NOT NULL,
	"item_count" INT NOT NULL,
	"coin_price" INT NOT NULL,
	"quantity" INT NOT NULL,
	"total_coins" INT NOT NULL,
	"delivery_mode" VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
	"status" VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
	PRIMARY KEY ("id"),
	KEY "idx_shop_acc" ("account_name"),
	KEY "idx_shop_char" ("character_id")
);
