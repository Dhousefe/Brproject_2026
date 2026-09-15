CREATE TABLE IF NOT EXISTS `site_shop_purchases` (
	`id` INT NOT NULL AUTO_INCREMENT,
	`created_at` BIGINT NOT NULL,
	`account_name` VARCHAR(45) NOT NULL,
	`character_id` INT NOT NULL DEFAULT 0,
	`character_name` VARCHAR(35) NOT NULL,
	`item_key` VARCHAR(60) NOT NULL,
	`item_id` INT NOT NULL,
	`item_name` VARCHAR(100) NOT NULL,
	`item_count` INT NOT NULL,
	`coin_price` INT NOT NULL,
	`quantity` INT NOT NULL,
	`total_coins` INT NOT NULL,
	`delivery_mode` VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
	`status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
	PRIMARY KEY (`id`)
);
