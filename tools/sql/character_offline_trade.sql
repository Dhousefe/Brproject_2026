CREATE TABLE IF NOT EXISTS `character_offline_trade` (
	`charId` INT NOT NULL,
	`time` BIGINT NOT NULL DEFAULT 0,
	`type` INT NOT NULL DEFAULT 0,
	`title` VARCHAR(100) DEFAULT NULL,
	PRIMARY KEY (`charId`)
);
