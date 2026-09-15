CREATE TABLE IF NOT EXISTS `autofarm_nodes` (
  `node_id` INT UNSIGNED NOT NULL DEFAULT 0,
  `area_id` INT NOT NULL DEFAULT 0,
  `loc_x` INT NOT NULL DEFAULT 0,
  `loc_y` INT NOT NULL DEFAULT 0,
  `loc_z` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`area_id`, `node_id`)
);