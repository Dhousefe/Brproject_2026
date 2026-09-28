CREATE TABLE IF NOT EXISTS `fail2ban_events` (
	`id` BIGINT NOT NULL AUTO_INCREMENT,
	`ts` BIGINT NOT NULL,
	`ip` VARCHAR(64) NOT NULL,
	`jail` VARCHAR(64) NOT NULL,
	`type` VARCHAR(32) NOT NULL,
	`detail` VARCHAR(512) DEFAULT '',
	PRIMARY KEY (`id`),
	KEY `idx_f2e_ts` (`ts`),
	KEY `idx_f2e_ip` (`ip`)
);
