-- Corrige erros de INSERT/UPDATE no mod PIX (qrcode PIX e link MP sao maiores que VARCHAR(180)).
-- Rode no mesmo banco do gameserver. Ignore erro se a tabela ainda nao existir — rode donations.sql antes.

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

CREATE TABLE IF NOT EXISTS donations_payments (
  `purchase_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `mp_preference_id` VARCHAR(52) DEFAULT NULL,
  `paypal_invoice_id` VARCHAR(25) DEFAULT NULL,
  `qrcode` TEXT DEFAULT NULL,
  `link` VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (`purchase_id`)
);

ALTER TABLE `donations_payments` MODIFY COLUMN `qrcode` TEXT DEFAULT NULL;
ALTER TABLE `donations_payments` MODIFY COLUMN `link` VARCHAR(512) DEFAULT NULL;
