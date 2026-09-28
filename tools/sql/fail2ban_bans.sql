CREATE TABLE IF NOT EXISTS `fail2ban_bans` (
	`id` BIGINT NOT NULL AUTO_INCREMENT,
	`ip` VARCHAR(64) NOT NULL,
	`jail` VARCHAR(64) NOT NULL,
	`reason` VARCHAR(255) DEFAULT '',
	`ban_time` BIGINT NOT NULL DEFAULT 0,
	`expire_time` BIGINT NOT NULL DEFAULT 0,
	`status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	`source` VARCHAR(32) DEFAULT 'AUTO',
	PRIMARY KEY (`id`),
	KEY `idx_f2b_ip_status` (`ip`, `status`),
	KEY `idx_f2b_expire` (`expire_time`)
);
