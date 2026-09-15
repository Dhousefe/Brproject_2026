CREATE TABLE IF NOT EXISTS `character_mission` (
  `object_id` INT(11) NOT NULL,
  `type` VARCHAR(50) NOT NULL,
  `level` INT(11) NOT NULL DEFAULT 0,
  `value` INT(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`object_id`, `type`),
  KEY `idx_object_id` (`object_id`)
);