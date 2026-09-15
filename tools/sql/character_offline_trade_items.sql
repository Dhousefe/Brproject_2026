CREATE TABLE IF NOT EXISTS `character_offline_trade_items` (
	`charId` INT NOT NULL,
	`item` INT NOT NULL DEFAULT 0,
	`count` BIGINT NOT NULL DEFAULT 0,
	`price` BIGINT NOT NULL DEFAULT 0,
	`enchant` INT NOT NULL DEFAULT 0,
	KEY `charId` (`charId`)
);
