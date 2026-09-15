/*
MySQL Data Transfer
Source Host: localhost
Source Database: rusacis
Target Host: localhost
Target Database: rusacis
Date: 29/12/2025 23:24:33
*/

SET FOREIGN_KEY_CHECKS=0;
-- ----------------------------
-- Table structure for autofarm_time_usage
-- ----------------------------
DROP TABLE IF EXISTS `autofarm_time_usage`;
CREATE TABLE `autofarm_time_usage` (
  `player_id` int(11) NOT NULL,
  `time_used` bigint(20) DEFAULT 0,
  `last_reset` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records 
-- ----------------------------
INSERT INTO `autofarm_time_usage` VALUES ('268492127', '-650000', '2025-09-13 00:58:49');
INSERT INTO `autofarm_time_usage` VALUES ('268493191', '693599978', '2025-12-19 19:50:03');
INSERT INTO `autofarm_time_usage` VALUES ('268493550', '8567683524', '2025-12-19 00:05:04');
