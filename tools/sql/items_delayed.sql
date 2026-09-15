CREATE TABLE IF NOT EXISTS `items_delayed` (
	`owner_id` INT NOT NULL,
	`item_id` INT NOT NULL,
	`count` BIGINT NOT NULL DEFAULT 1,
	`enchant_level` INT NOT NULL DEFAULT 0,
	`payment_status` INT NOT NULL DEFAULT 0,
	`description` VARCHAR(255) DEFAULT NULL,
	KEY `owner_id` (`owner_id`)
);
