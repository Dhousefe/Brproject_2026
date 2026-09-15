CREATE TABLE IF NOT EXISTS donations_payments (
  `purchase_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `mp_preference_id` VARCHAR(52) DEFAULT NULL,
  `paypal_invoice_id` VARCHAR(25) DEFAULT NULL,
  `qrcode` TEXT DEFAULT NULL,
  `link` VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (`purchase_id`)
);
