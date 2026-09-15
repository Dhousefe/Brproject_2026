CREATE TABLE IF NOT EXISTS `accounts_hardware_guard` (
	`login` VARCHAR(45) NOT NULL,
	`credential_id` VARCHAR(255) NOT NULL,
	`public_key_der` TEXT NOT NULL,
	`algorithm` INT NOT NULL DEFAULT -7,
	`device_name` VARCHAR(120) NOT NULL DEFAULT 'Windows Hello (TPM 2.0)',
	`sign_count` BIGINT NOT NULL DEFAULT 0,
	`created_at` BIGINT NOT NULL DEFAULT 0,
	`last_used_at` BIGINT NOT NULL DEFAULT 0,
	PRIMARY KEY (`login`, `credential_id`)
);

CREATE INDEX IF NOT EXISTS `idx_hw_guard_login` ON `accounts_hardware_guard` (`login`);
