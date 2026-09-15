CREATE TABLE IF NOT EXISTS donations (
  `purchase_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `payment_id` VARCHAR(30) DEFAULT NULL,
  `payment_method` VARCHAR(7) DEFAULT NULL,
  `player_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `email` VARCHAR(44) NOT NULL DEFAULT '',
  `product_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `quantity` INT UNSIGNED NOT NULL DEFAULT 0,
  `unit_price` DOUBLE NOT NULL DEFAULT 0,
  `currency` VARCHAR(3) DEFAULT NULL,
  `date` BIGINT UNSIGNED DEFAULT 0,
  `status` VARCHAR(10) NOT NULL DEFAULT '',
  `terms` TINYINT UNSIGNED DEFAULT 0,
  PRIMARY KEY (`purchase_id`),
  UNIQUE KEY (`purchase_id`, `payment_id`)
);
