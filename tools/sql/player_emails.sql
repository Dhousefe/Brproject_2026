/*
MySQL Data Transfer
Source Host: localhost
Source Database: l2jdb
Target Host: localhost
Target Database: l2jdb
Date: 15/09/2025 14:10:55
*/

SET FOREIGN_KEY_CHECKS=0;
-- ----------------------------
-- Table structure for player_emails
-- ----------------------------
DROP TABLE IF EXISTS `player_emails`;
CREATE TABLE `player_emails` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sender_id` int(11) NOT NULL,
  `target_id` int(11) NOT NULL,
  `email_id` int(11) NOT NULL,
  `item_object_id` int(11) NOT NULL,
  `item_id` smallint(6) NOT NULL,
  `count` int(11) NOT NULL,
  `enchant_level` smallint(6) NOT NULL,
  `is_augmented` tinyint(1) NOT NULL DEFAULT 0,
  `augment_id` int(11) DEFAULT NULL,
  `is_paid` tinyint(1) NOT NULL DEFAULT 0,
  `payment_item_id` smallint(6) DEFAULT NULL,
  `payment_item_count` int(11) DEFAULT NULL,
  `status` enum('PENDING','ACCEPTED','REJECTED','CLAIMED','EXPIRED') DEFAULT 'PENDING',
  `expiration_time` bigint(20) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Records 
-- ----------------------------
INSERT INTO `player_emails` VALUES ('1', '268493565', '268493689', '268493695', '268493568', '10', '1', '0', '0', '0', '0', null, null, 'EXPIRED', '1757878698193', '1757878398193');
INSERT INTO `player_emails` VALUES ('2', '268493565', '268493689', '268493583', '268493809', '10', '1', '0', '0', '0', '0', null, null, 'EXPIRED', '1757879677331', '1757879377332');
INSERT INTO `player_emails` VALUES ('3', '268493565', '268493689', '268493704', '268493703', '10', '1', '0', '0', '0', '0', null, null, 'EXPIRED', '1757880016428', '1757879716428');
INSERT INTO `player_emails` VALUES ('4', '268493565', '268493689', '268494767', '268493940', '10', '1', '0', '0', '0', '0', null, null, 'CLAIMED', '1757880470857', '1757880170857');
