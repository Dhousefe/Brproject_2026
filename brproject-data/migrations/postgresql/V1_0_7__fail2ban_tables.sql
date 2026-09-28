-- =========================================================================
-- V1_0_7: Criação das tabelas do Fail2Ban para MariaDB / MySQL / PostgreSQL
-- =========================================================================

CREATE TABLE IF NOT EXISTS "fail2ban_bans" (
	"id" BIGINT NOT NULL ,
	"ip" VARCHAR(64) NOT NULL,
	"jail" VARCHAR(64) NOT NULL,
	"reason" VARCHAR(255) DEFAULT '',
	"ban_time" BIGINT NOT NULL DEFAULT 0,
	"expire_time" BIGINT NOT NULL DEFAULT 0,
	"status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	"source" VARCHAR(32) DEFAULT 'AUTO',
	PRIMARY KEY ("id"),
	KEY "idx_f2b_ip_status" ("ip", "status"),
	KEY "idx_f2b_expire" ("expire_time")
);

CREATE TABLE IF NOT EXISTS "fail2ban_events" (
	"id" BIGINT NOT NULL ,
	"ts" BIGINT NOT NULL,
	"ip" VARCHAR(64) NOT NULL,
	"jail" VARCHAR(64) NOT NULL,
	"type" VARCHAR(32) NOT NULL,
	"detail" VARCHAR(512) DEFAULT '',
	PRIMARY KEY ("id"),
	KEY "idx_f2e_ts" ("ts"),
	KEY "idx_f2e_ip" ("ip")
);
