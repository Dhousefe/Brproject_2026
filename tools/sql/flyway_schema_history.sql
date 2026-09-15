CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
	`installed_rank` INT NOT NULL,
	`version` VARCHAR(50) DEFAULT NULL,
	`description` VARCHAR(200) NOT NULL,
	`type` VARCHAR(20) NOT NULL,
	`script` VARCHAR(1000) NOT NULL,
	`checksum` INT DEFAULT NULL,
	`installed_by` VARCHAR(100) NOT NULL,
	`installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`execution_time` INT NOT NULL,
	`success` TINYINT(1) NOT NULL,
	PRIMARY KEY (`installed_rank`)
);
