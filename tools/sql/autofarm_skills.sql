/*
MySQL Data Transfer
Source Host: localhost
Source Database: rusacis
Target Host: localhost
Target Database: rusacis
Date: 29/12/2025 23:24:16
*/

SET FOREIGN_KEY_CHECKS=0;
-- ----------------------------
-- Table structure for autofarm_skills
-- ----------------------------
DROP TABLE IF EXISTS `autofarm_skills`;
CREATE TABLE `autofarm_skills` (
  `player_id` int(11) NOT NULL,
  `skill_id` int(11) NOT NULL,
  `slot` int(11) NOT NULL,
  PRIMARY KEY (`player_id`,`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records 
-- ----------------------------
INSERT INTO `autofarm_skills` VALUES ('268493550', '1071', '4');
INSERT INTO `autofarm_skills` VALUES ('268493550', '1235', '0');
INSERT INTO `autofarm_skills` VALUES ('268493550', '1265', '3');
INSERT INTO `autofarm_skills` VALUES ('268493550', '1340', '1');
INSERT INTO `autofarm_skills` VALUES ('268493550', '1342', '2');
