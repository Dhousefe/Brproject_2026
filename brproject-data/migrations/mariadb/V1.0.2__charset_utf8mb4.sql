-- V1_0_2: Convert residual latin1 / utf8 (utf8mb3) tables to utf8mb4.
-- Safe on existing DBs that still have legacy charsets from older tools/sql.
-- New installs already get utf8mb4 from V1_0_0 baseline (regenerated from tools/sql).
-- Prefer utf8mb4_unicode_ci; CONVERT TO rewrites columns/indexes as needed.
-- Tables remain InnoDB (no ENGINE change).

ALTER TABLE `accounts` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `autofarm_player_data` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `autofarm_skills` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `hwid_bans` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `hwid_info` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `hwid_extra_boxes` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `player_emails` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `buffshop` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `character_data` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `character_offline_trade` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `character_offline_trade_items` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `items_delayed` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
