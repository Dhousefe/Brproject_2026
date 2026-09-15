CREATE TABLE IF NOT EXISTS `fake_players` (
	`obj_id` INT NOT NULL,
	`char_name` VARCHAR(35) NOT NULL,
	`class_id` VARCHAR(10) NOT NULL,
	`level` INT DEFAULT 81,
	`x` INT DEFAULT 0,
	`y` INT DEFAULT 0,
	`z` INT DEFAULT 0,
	`face` INT DEFAULT 0,
	`hair_style` INT DEFAULT 0,
	`hair_color` INT DEFAULT 0,
	`sex` INT DEFAULT 0,
	`archetype` VARCHAR(45) DEFAULT 'Farmer',
	`persistent` INT DEFAULT 0,
	`created_at` BIGINT DEFAULT 0,
	PRIMARY KEY (`obj_id`)
);
