/*
 Navicat Premium Data Transfer

 Source Server         : servidor
 Source Server Type    : MariaDB
 Source Server Version : 110404
 Source Host           : 192.168.0.168:3306
 Source Schema         : 409

 Target Server Type    : MariaDB
 Target Server Version : 110404
 File Encoding         : 65001

 Date: 21/07/2025 22:53:05
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for hwid_bans
-- ----------------------------
DROP TABLE IF EXISTS `hwid_bans`;
CREATE TABLE `hwid_bans`  (
  `HWID` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `HWIDSecond` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `expiretime` int(11) NOT NULL DEFAULT 0,
  `comments` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '',
  UNIQUE INDEX `HWID`(`HWID`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hwid_extra_boxes
-- ----------------------------
DROP TABLE IF EXISTS `hwid_extra_boxes`;
CREATE TABLE `hwid_extra_boxes`  (
  `hwid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `extra_boxes` int(11) NOT NULL,
  PRIMARY KEY (`hwid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hwid_info
-- ----------------------------
DROP TABLE IF EXISTS `hwid_info`;
CREATE TABLE `hwid_info`  (
  `HWID` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `Account` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `PlayerID` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `LockType` enum('PLAYER_LOCK','ACCOUNT_LOCK','NONE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  PRIMARY KEY (`HWID`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
