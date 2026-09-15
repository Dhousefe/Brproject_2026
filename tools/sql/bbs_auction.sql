CREATE TABLE IF NOT EXISTS `bbs_auction` (
	`id` INT(10) NOT NULL DEFAULT '0',
	`obj_Id` INT(10) NOT NULL DEFAULT '0',
	`item_id` INT(10) NOT NULL DEFAULT '0',
	`item_count` INT(10) NOT NULL DEFAULT '0',
	`item_enchant` INT(10) NOT NULL DEFAULT '0',
	`price_id` INT(10) NOT NULL DEFAULT '0',
	`price_count` INT(10) NOT NULL DEFAULT '0',
	`duration` BIGINT UNSIGNED DEFAULT NULL,
	PRIMARY KEY (`id`)
);