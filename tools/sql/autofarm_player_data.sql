/*
MySQL Data Transfer
Source Host: localhost
Source Database: rusacis
Target Host: localhost
Target Database: rusacis
Date: 29/12/2025 23:24:26
*/

SET FOREIGN_KEY_CHECKS=0;
-- ----------------------------
-- Table structure for autofarm_player_data
-- ----------------------------
DROP TABLE IF EXISTS `autofarm_player_data`;
CREATE TABLE IF NOT EXISTS `autofarm_player_data` (
  `player_id` INT(11) NOT NULL,
  `time_used` BIGINT(20) DEFAULT 0,
  `cycle_start_time` BIGINT(20) DEFAULT 0,
  `extra_time` BIGINT(20) DEFAULT 0,
  PRIMARY KEY (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records 
-- ----------------------------
INSERT INTO `autofarm_player_data` VALUES ('268493550', '6831384');
