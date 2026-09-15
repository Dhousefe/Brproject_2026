CREATE TABLE IF NOT EXISTS autofarm_areas (
  `player_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `area_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `name` VARCHAR(44) DEFAULT NULL,
  `type` VARCHAR(4) DEFAULT NULL,
  PRIMARY KEY (`player_id`, `area_id`)
);
