-- SQLite schema generated from data/Backup.sql
PRAGMA foreign_keys=OFF;
BEGIN TRANSACTION;
CREATE TABLE IF NOT EXISTS "account_premium" (
  "account_name" TEXT NOT NULL DEFAULT '',
  "premium_service" INTEGER NOT NULL DEFAULT 0,
  "enddate" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("account_name")
);
CREATE TABLE IF NOT EXISTS "accounts" (
  "login" TEXT NOT NULL DEFAULT '',
  "password" TEXT NOT NULL DEFAULT '',
  "last_active" INTEGER NOT NULL DEFAULT 0,
  "access_level" INTEGER NOT NULL DEFAULT 0,
  "last_server" INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY ("login")
);
CREATE TABLE IF NOT EXISTS "auctions" (
  "clanhall_id" INTEGER NOT NULL DEFAULT 0,
  "bidder_name" TEXT NOT NULL DEFAULT '',
  "clan_oid" INTEGER NOT NULL DEFAULT 0,
  "clan_name" TEXT NOT NULL DEFAULT '',
  "max_bid" INTEGER NOT NULL DEFAULT 0,
  "time_bid" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clanhall_id", "clan_oid")
);
CREATE TABLE IF NOT EXISTS "augmentations" (
  "item_oid" INTEGER NOT NULL DEFAULT 0,
  "attributes" INTEGER NOT NULL DEFAULT -1,
  "skill_id" INTEGER NOT NULL DEFAULT -1,
  "skill_level" INTEGER NOT NULL DEFAULT -1,
  PRIMARY KEY ("item_oid")
);
CREATE TABLE IF NOT EXISTS "autofarm_areas" (
  "player_id" INTEGER NOT NULL DEFAULT 0,
  "area_id" INTEGER NOT NULL DEFAULT 0,
  "name" TEXT DEFAULT NULL,
  "type" TEXT DEFAULT NULL,
  PRIMARY KEY ("player_id", "area_id")
);
CREATE TABLE IF NOT EXISTS "autofarm_nodes" (
  "node_id" INTEGER NOT NULL DEFAULT 0,
  "area_id" INTEGER NOT NULL DEFAULT 0,
  "loc_x" INTEGER NOT NULL DEFAULT 0,
  "loc_y" INTEGER NOT NULL DEFAULT 0,
  "loc_z" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("area_id", "node_id")
);
CREATE TABLE IF NOT EXISTS "autofarm_player_data" (
  "player_id" INTEGER NOT NULL,
  "time_used" INTEGER DEFAULT 0,
  PRIMARY KEY ("player_id")
);
CREATE TABLE IF NOT EXISTS "autofarm_skills" (
  "player_id" INTEGER NOT NULL,
  "skill_id" INTEGER NOT NULL,
  "slot" INTEGER NOT NULL,
  PRIMARY KEY ("player_id", "skill_id")
);
CREATE TABLE IF NOT EXISTS "autofarm_time_usage" (
  "player_id" INTEGER NOT NULL,
  "time_used" INTEGER DEFAULT 0,
  "last_reset" TEXT DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY ("player_id")
);
CREATE TABLE IF NOT EXISTS "balance_classes" (
  "class_id_attacker" INTEGER NOT NULL,
  "class_id_target" INTEGER NOT NULL,
  "p_atk_mod" INTEGER DEFAULT 1.00,
  "m_atk_mod" INTEGER DEFAULT 1.00,
  "p_def_mod" INTEGER DEFAULT 1.00,
  "m_def_mod" INTEGER DEFAULT 1.00,
  PRIMARY KEY ("class_id_attacker", "class_id_target")
);
CREATE TABLE IF NOT EXISTS "balance_vulnerability" (
  "skill_type" TEXT NOT NULL,
  "multiplier" INTEGER NOT NULL DEFAULT 1.00,
  PRIMARY KEY ("skill_type")
);
CREATE TABLE IF NOT EXISTS "bbs_auction" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "obj_Id" INTEGER NOT NULL DEFAULT 0,
  "item_id" INTEGER NOT NULL DEFAULT 0,
  "item_count" INTEGER NOT NULL DEFAULT 0,
  "item_enchant" INTEGER NOT NULL DEFAULT 0,
  "price_id" INTEGER NOT NULL DEFAULT 0,
  "price_count" INTEGER NOT NULL DEFAULT 0,
  "duration" INTEGER DEFAULT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "bbs_favorite" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "player_id" INTEGER NOT NULL DEFAULT 0,
  "title" TEXT DEFAULT NULL,
  "bypass" TEXT DEFAULT NULL,
  "date" TEXT NULL DEFAULT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "bbs_forum" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "type" TEXT NOT NULL DEFAULT '0',
  "access" TEXT NOT NULL DEFAULT '0',
  "owner_id" INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS "idx_bbs_forum_id" ON "bbs_forum" ("id");
CREATE TABLE IF NOT EXISTS "bbs_mail" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "receiver_id" INTEGER NOT NULL DEFAULT 0,
  "sender_id" INTEGER NOT NULL DEFAULT 0,
  "location" TEXT NOT NULL,
  "recipients" TEXT DEFAULT NULL,
  "subject" TEXT DEFAULT NULL,
  "message" TEXT DEFAULT NULL,
  "sent_date" TEXT NULL DEFAULT NULL,
  "is_unread" INTEGER DEFAULT 1,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "bbs_post" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "owner_name" TEXT NOT NULL DEFAULT '',
  "owner_id" INTEGER NOT NULL DEFAULT 0,
  "date" INTEGER NOT NULL DEFAULT 0,
  "topic_id" INTEGER NOT NULL DEFAULT 0,
  "forum_id" INTEGER NOT NULL DEFAULT 0,
  "txt" TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS "bbs_topic" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "forum_id" INTEGER NOT NULL DEFAULT 0,
  "name" TEXT NOT NULL DEFAULT '',
  "date" INTEGER NOT NULL DEFAULT 0,
  "owner_name" TEXT NOT NULL DEFAULT '0',
  "owner_id" INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS "bookmarks" (
  "name" TEXT NOT NULL DEFAULT '',
  "obj_Id" INTEGER NOT NULL DEFAULT 0,
  "x" INTEGER DEFAULT NULL,
  "y" INTEGER DEFAULT NULL,
  "z" INTEGER DEFAULT NULL,
  PRIMARY KEY ("name", "obj_Id")
);
CREATE TABLE IF NOT EXISTS "buffer_schemes" (
  "object_id" INTEGER NOT NULL DEFAULT 0,
  "scheme_name" TEXT NOT NULL DEFAULT 'default',
  "skills" TEXT NOT NULL,
  "levels" TEXT NOT NULL,
  PRIMARY KEY ("object_id", "scheme_name")
);
CREATE TABLE IF NOT EXISTS "buffshop" (
  "ownerId" INTEGER NOT NULL,
  "buffs" TEXT NOT NULL,
  "title" TEXT NOT NULL DEFAULT '',
  "x" INTEGER DEFAULT NULL,
  "y" INTEGER DEFAULT NULL,
  "z" INTEGER DEFAULT NULL,
  "heading" INTEGER DEFAULT NULL,
  "tempBuffShopPrice" TEXT DEFAULT NULL,
  "store_message" TEXT DEFAULT NULL,
  "value" TEXT DEFAULT NULL,
  "class_id" INTEGER NOT NULL DEFAULT 0,
  "sex" INTEGER NOT NULL DEFAULT 0,
  "face" INTEGER NOT NULL DEFAULT 0,
  "hair_style" INTEGER NOT NULL DEFAULT 0,
  "hair_color" INTEGER NOT NULL DEFAULT 0,
  "equipped_items" TEXT DEFAULT NULL,
  PRIMARY KEY ("ownerId")
);
CREATE TABLE IF NOT EXISTS "buylists" (
  "buylist_id" INTEGER NOT NULL,
  "item_id" INTEGER NOT NULL,
  "count" INTEGER NOT NULL DEFAULT 0,
  "next_restock_time" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("buylist_id", "item_id")
);
CREATE TABLE IF NOT EXISTS "castle" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "currentTaxPercent" INTEGER NOT NULL DEFAULT 0,
  "nextTaxPercent" INTEGER NOT NULL DEFAULT 0,
  "treasury" INTEGER NOT NULL DEFAULT 0,
  "taxRevenue" INTEGER NOT NULL DEFAULT 0,
  "seedIncome" INTEGER NOT NULL DEFAULT 0,
  "siegeDate" INTEGER NOT NULL DEFAULT 0,
  "regTimeOver" TEXT NOT NULL DEFAULT 'true',
  "certificates" INTEGER NOT NULL DEFAULT 300,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "castle_doorupgrade" (
  "doorId" INTEGER NOT NULL DEFAULT 0,
  "hp" INTEGER NOT NULL DEFAULT 0,
  "castleId" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("doorId")
);
CREATE TABLE IF NOT EXISTS "castle_functions" (
  "castle_id" INTEGER NOT NULL DEFAULT 0,
  "type" INTEGER NOT NULL DEFAULT 0,
  "lvl" INTEGER NOT NULL DEFAULT 0,
  "lease" INTEGER NOT NULL DEFAULT 0,
  "rate" INTEGER NOT NULL DEFAULT 0,
  "endTime" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("castle_id", "type")
);
CREATE TABLE IF NOT EXISTS "castle_manor_procure" (
  "castle_id" INTEGER NOT NULL DEFAULT 0,
  "crop_id" INTEGER NOT NULL DEFAULT 0,
  "amount" INTEGER NOT NULL DEFAULT 0,
  "start_amount" INTEGER NOT NULL DEFAULT 0,
  "price" INTEGER NOT NULL DEFAULT 0,
  "reward_type" INTEGER NOT NULL DEFAULT 0,
  "next_period" INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY ("castle_id", "crop_id", "next_period")
);
CREATE TABLE IF NOT EXISTS "castle_manor_production" (
  "castle_id" INTEGER NOT NULL DEFAULT 0,
  "seed_id" INTEGER NOT NULL DEFAULT 0,
  "amount" INTEGER NOT NULL DEFAULT 0,
  "start_amount" INTEGER NOT NULL DEFAULT 0,
  "price" INTEGER NOT NULL DEFAULT 0,
  "next_period" INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY ("castle_id", "seed_id", "next_period")
);
CREATE TABLE IF NOT EXISTS "castle_trapupgrade" (
  "castleId" INTEGER NOT NULL DEFAULT 0,
  "towerIndex" INTEGER NOT NULL DEFAULT 0,
  "level" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("towerIndex", "castleId")
);
CREATE TABLE IF NOT EXISTS "character_data" (
  "charId" INTEGER NOT NULL,
  "valueName" TEXT NOT NULL,
  "valueData" TEXT DEFAULT NULL,
  PRIMARY KEY ("charId", "valueName")
);
CREATE TABLE IF NOT EXISTS "character_hennas" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "symbol_id" INTEGER DEFAULT NULL,
  "slot" INTEGER NOT NULL DEFAULT 0,
  "class_index" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_obj_id", "slot", "class_index")
);
CREATE TABLE IF NOT EXISTS "character_macroses" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "id" INTEGER NOT NULL DEFAULT 0,
  "icon" INTEGER DEFAULT NULL,
  "name" TEXT DEFAULT NULL,
  "descr" TEXT DEFAULT NULL,
  "acronym" TEXT DEFAULT NULL,
  "commands" TEXT DEFAULT NULL,
  PRIMARY KEY ("char_obj_id", "id")
);
CREATE TABLE IF NOT EXISTS "character_memo" (
  "charId" INTEGER NOT NULL,
  "var" TEXT NOT NULL,
  "val" TEXT NOT NULL,
  PRIMARY KEY ("charId", "var")
);
CREATE TABLE IF NOT EXISTS "character_mission" (
  "object_id" INTEGER NOT NULL,
  "type" TEXT NOT NULL,
  "level" INTEGER NOT NULL DEFAULT 0,
  "value" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("object_id", "type")
);
CREATE INDEX IF NOT EXISTS "idx_character_mission_idx_object_id" ON "character_mission" ("object_id");
CREATE TABLE IF NOT EXISTS "character_offline_trade" (
  "charId" INTEGER NOT NULL,
  "time" INTEGER NOT NULL DEFAULT 0,
  "type" INTEGER NOT NULL DEFAULT 0,
  "title" TEXT DEFAULT NULL,
  PRIMARY KEY ("charId")
);
CREATE TABLE IF NOT EXISTS "character_offline_trade_items" (
  "charId" INTEGER NOT NULL,
  "item" INTEGER NOT NULL DEFAULT 0,
  "count" INTEGER NOT NULL DEFAULT 0,
  "price" INTEGER NOT NULL DEFAULT 0,
  "enchant" INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS "idx_character_offline_trade_items_charId" ON "character_offline_trade_items" ("charId");
CREATE INDEX IF NOT EXISTS "idx_character_offline_trade_items_item" ON "character_offline_trade_items" ("item");
CREATE TABLE IF NOT EXISTS "character_quests" (
  "charId" INTEGER NOT NULL DEFAULT 0,
  "name" TEXT NOT NULL DEFAULT '',
  "var" TEXT NOT NULL DEFAULT '',
  "value" TEXT DEFAULT NULL,
  PRIMARY KEY ("charId", "name", "var")
);
CREATE TABLE IF NOT EXISTS "character_raid_points" (
  "char_id" INTEGER NOT NULL DEFAULT 0,
  "boss_id" INTEGER NOT NULL DEFAULT 0,
  "points" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_id", "boss_id")
);
CREATE TABLE IF NOT EXISTS "character_recipebook" (
  "charId" INTEGER NOT NULL DEFAULT 0,
  "recipeId" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("charId", "recipeId")
);
CREATE TABLE IF NOT EXISTS "character_recommends" (
  "char_id" INTEGER NOT NULL DEFAULT 0,
  "target_id" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_id", "target_id")
);
CREATE TABLE IF NOT EXISTS "character_relations" (
  "char_id" INTEGER NOT NULL DEFAULT 0,
  "friend_id" INTEGER NOT NULL DEFAULT 0,
  "relation" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_id", "friend_id")
);
CREATE TABLE IF NOT EXISTS "character_shortcuts" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "slot" INTEGER NOT NULL DEFAULT 0,
  "page" INTEGER NOT NULL DEFAULT 0,
  "type" TEXT NOT NULL DEFAULT 'NONE',
  "id" INTEGER NOT NULL DEFAULT 0,
  "level" INTEGER NOT NULL DEFAULT 0,
  "class_index" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_obj_id", "slot", "page", "class_index")
);
CREATE INDEX IF NOT EXISTS "idx_character_shortcuts_id" ON "character_shortcuts" ("id");
CREATE TABLE IF NOT EXISTS "character_skills" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "skill_id" INTEGER NOT NULL DEFAULT 0,
  "skill_level" INTEGER NOT NULL DEFAULT 1,
  "class_index" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_obj_id", "skill_id", "class_index")
);
CREATE TABLE IF NOT EXISTS "character_skills_save" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "skill_id" INTEGER NOT NULL DEFAULT 0,
  "skill_level" INTEGER NOT NULL DEFAULT 1,
  "effect_count" INTEGER NOT NULL DEFAULT 0,
  "effect_cur_time" INTEGER NOT NULL DEFAULT 0,
  "reuse_delay" INTEGER NOT NULL DEFAULT 0,
  "systime" INTEGER NOT NULL DEFAULT 0,
  "restore_type" INTEGER NOT NULL DEFAULT 0,
  "class_index" INTEGER NOT NULL DEFAULT 0,
  "buff_index" INTEGER NOT NULL DEFAULT 0,
  "npc" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_obj_id", "skill_id", "skill_level", "class_index")
);
CREATE TABLE IF NOT EXISTS "character_subclasses" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "class_id" INTEGER NOT NULL DEFAULT 0,
  "exp" INTEGER NOT NULL DEFAULT 0,
  "sp" INTEGER NOT NULL DEFAULT 0,
  "level" INTEGER NOT NULL DEFAULT 40,
  "class_index" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_obj_id", "class_id")
);
CREATE TABLE IF NOT EXISTS "characters" (
  "account_name" TEXT DEFAULT NULL,
  "obj_Id" INTEGER NOT NULL DEFAULT 0,
  "char_name" TEXT NOT NULL,
  "level" INTEGER DEFAULT NULL,
  "maxHp" INTEGER DEFAULT NULL,
  "curHp" INTEGER DEFAULT NULL,
  "maxCp" INTEGER DEFAULT NULL,
  "curCp" INTEGER DEFAULT NULL,
  "maxMp" INTEGER DEFAULT NULL,
  "curMp" INTEGER DEFAULT NULL,
  "face" INTEGER DEFAULT NULL,
  "hairStyle" INTEGER DEFAULT NULL,
  "hairColor" INTEGER DEFAULT NULL,
  "sex" INTEGER DEFAULT NULL,
  "heading" INTEGER DEFAULT NULL,
  "x" INTEGER DEFAULT NULL,
  "y" INTEGER DEFAULT NULL,
  "z" INTEGER DEFAULT NULL,
  "exp" INTEGER DEFAULT 0,
  "expBeforeDeath" INTEGER DEFAULT 0,
  "sp" INTEGER NOT NULL DEFAULT 0,
  "karma" INTEGER DEFAULT NULL,
  "pvpkills" INTEGER DEFAULT NULL,
  "pkkills" INTEGER DEFAULT NULL,
  "clanid" INTEGER DEFAULT NULL,
  "race" INTEGER DEFAULT NULL,
  "classid" INTEGER DEFAULT NULL,
  "base_class" INTEGER NOT NULL DEFAULT 0,
  "deletetime" INTEGER DEFAULT NULL,
  "title" TEXT DEFAULT NULL,
  "rec_have" INTEGER NOT NULL DEFAULT 0,
  "rec_left" INTEGER NOT NULL DEFAULT 0,
  "accesslevel" INTEGER DEFAULT 0,
  "online" INTEGER DEFAULT NULL,
  "onlinetime" INTEGER DEFAULT NULL,
  "lastAccess" INTEGER DEFAULT NULL,
  "wantspeace" INTEGER DEFAULT 0,
  "isin7sdungeon" INTEGER NOT NULL DEFAULT 0,
  "punish_level" INTEGER NOT NULL DEFAULT 0,
  "punish_timer" INTEGER NOT NULL DEFAULT 0,
  "power_grade" INTEGER DEFAULT NULL,
  "nobless" INTEGER NOT NULL DEFAULT 0,
  "hero" INTEGER NOT NULL DEFAULT 0,
  "subpledge" INTEGER NOT NULL DEFAULT 0,
  "lvl_joined_academy" INTEGER NOT NULL DEFAULT 0,
  "apprentice" INTEGER NOT NULL DEFAULT 0,
  "sponsor" INTEGER NOT NULL DEFAULT 0,
  "varka_ketra_ally" INTEGER NOT NULL DEFAULT 0,
  "clan_join_expiry_time" INTEGER NOT NULL DEFAULT 0,
  "clan_create_expiry_time" INTEGER NOT NULL DEFAULT 0,
  "death_penalty_level" INTEGER NOT NULL DEFAULT 0,
  "herountil" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("obj_Id")
);
CREATE INDEX IF NOT EXISTS "idx_characters_clanid" ON "characters" ("clanid");
CREATE TABLE IF NOT EXISTS "clan_data" (
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  "clan_name" TEXT DEFAULT NULL,
  "clan_level" INTEGER NOT NULL DEFAULT 0,
  "reputation_score" INTEGER NOT NULL DEFAULT 0,
  "hasCastle" INTEGER NOT NULL DEFAULT 0,
  "ally_id" INTEGER NOT NULL DEFAULT 0,
  "ally_name" TEXT DEFAULT NULL,
  "leader_id" INTEGER NOT NULL DEFAULT 0,
  "new_leader_id" INTEGER NOT NULL DEFAULT 0,
  "crest_id" INTEGER NOT NULL DEFAULT 0,
  "crest_large_id" INTEGER NOT NULL DEFAULT 0,
  "ally_crest_id" INTEGER NOT NULL DEFAULT 0,
  "auction_bid_at" INTEGER NOT NULL DEFAULT 0,
  "ally_penalty_expiry_time" INTEGER NOT NULL DEFAULT 0,
  "ally_penalty_type" INTEGER NOT NULL DEFAULT 0,
  "char_penalty_expiry_time" INTEGER NOT NULL DEFAULT 0,
  "dissolving_expiry_time" INTEGER NOT NULL DEFAULT 0,
  "enabled" INTEGER NOT NULL DEFAULT 0,
  "notice" TEXT DEFAULT NULL,
  "introduction" TEXT DEFAULT NULL,
  "graduates" TEXT DEFAULT NULL,
  PRIMARY KEY ("clan_id")
);
CREATE INDEX IF NOT EXISTS "idx_clan_data_leader_id" ON "clan_data" ("leader_id");
CREATE INDEX IF NOT EXISTS "idx_clan_data_ally_id" ON "clan_data" ("ally_id");
CREATE TABLE IF NOT EXISTS "clan_privs" (
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  "ranking" INTEGER NOT NULL DEFAULT 0,
  "privs" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clan_id", "ranking")
);
CREATE TABLE IF NOT EXISTS "clan_skills" (
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  "skill_id" INTEGER NOT NULL DEFAULT 0,
  "skill_level" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clan_id", "skill_id")
);
CREATE TABLE IF NOT EXISTS "clan_subpledges" (
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  "sub_pledge_id" INTEGER NOT NULL DEFAULT 0,
  "name" TEXT DEFAULT NULL,
  "leader_id" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clan_id", "sub_pledge_id")
);
CREATE TABLE IF NOT EXISTS "clan_wars" (
  "clan1" TEXT NOT NULL DEFAULT '',
  "clan2" TEXT NOT NULL DEFAULT '',
  "expiry_time" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clan1", "clan2")
);
CREATE TABLE IF NOT EXISTS "clanhall" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "ownerId" INTEGER NOT NULL DEFAULT 0,
  "paidUntil" INTEGER NOT NULL DEFAULT 0,
  "paid" INTEGER NOT NULL DEFAULT 0,
  "sellerBid" INTEGER NOT NULL DEFAULT 0,
  "sellerName" TEXT NOT NULL DEFAULT '',
  "sellerClanName" TEXT NOT NULL DEFAULT '',
  "endDate" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "clanhall_flagwar_attackers" (
  "clanhall_id" INTEGER NOT NULL DEFAULT 0,
  "flag" INTEGER NOT NULL DEFAULT 0,
  "npc" INTEGER NOT NULL DEFAULT 0,
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("flag")
);
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_attackers_hall_id" ON "clanhall_flagwar_attackers" ("clanhall_id");
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_attackers_clan_id" ON "clanhall_flagwar_attackers" ("clan_id");
CREATE TABLE IF NOT EXISTS "clanhall_flagwar_members" (
  "clanhall_id" INTEGER NOT NULL DEFAULT 0,
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  "object_id" INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_members_clanhall_id" ON "clanhall_flagwar_members" ("clanhall_id");
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_members_clan_id" ON "clanhall_flagwar_members" ("clan_id");
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_members_object_id" ON "clanhall_flagwar_members" ("object_id");
CREATE TABLE IF NOT EXISTS "clanhall_flagwar_owner_npcs" (
  "clanhall_id" INTEGER NOT NULL DEFAULT 0,
  "npc_id" INTEGER NOT NULL DEFAULT 0,
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clanhall_id")
);
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_owner_npcs_npc_id" ON "clanhall_flagwar_owner_npcs" ("npc_id");
CREATE INDEX IF NOT EXISTS "idx_clanhall_flagwar_owner_npcs_clan_id" ON "clanhall_flagwar_owner_npcs" ("clan_id");
CREATE TABLE IF NOT EXISTS "clanhall_functions" (
  "hall_id" INTEGER NOT NULL DEFAULT 0,
  "type" INTEGER NOT NULL DEFAULT 0,
  "lvl" INTEGER NOT NULL DEFAULT 0,
  "lease" INTEGER NOT NULL DEFAULT 0,
  "rate" INTEGER NOT NULL DEFAULT 0,
  "endTime" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("hall_id", "type")
);
CREATE TABLE IF NOT EXISTS "clanhall_siege_attackers" (
  "clanhall_id" INTEGER NOT NULL DEFAULT 0,
  "attacker_id" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clanhall_id", "attacker_id")
);
CREATE TABLE IF NOT EXISTS "cursed_weapons" (
  "itemId" INTEGER NOT NULL,
  "playerId" INTEGER DEFAULT 0,
  "playerKarma" INTEGER DEFAULT 0,
  "playerPkKills" INTEGER DEFAULT 0,
  "nbKills" INTEGER DEFAULT 0,
  "currentStage" INTEGER DEFAULT 0,
  "numberBeforeNextStage" INTEGER DEFAULT 0,
  "hungryTime" INTEGER DEFAULT 0,
  "endTime" INTEGER DEFAULT 0,
  PRIMARY KEY ("itemId")
);
CREATE TABLE IF NOT EXISTS "donations" (
  "purchase_id" INTEGER NOT NULL DEFAULT 0,
  "payment_id" TEXT DEFAULT NULL,
  "payment_method" TEXT DEFAULT NULL,
  "player_id" INTEGER NOT NULL DEFAULT 0,
  "email" TEXT NOT NULL DEFAULT '',
  "product_id" INTEGER NOT NULL DEFAULT 0,
  "quantity" INTEGER NOT NULL DEFAULT 0,
  "unit_price" REAL NOT NULL DEFAULT 0,
  "currency" TEXT DEFAULT NULL,
  "date" INTEGER DEFAULT 0,
  "status" TEXT NOT NULL DEFAULT '',
  "terms" INTEGER DEFAULT 0,
  PRIMARY KEY ("purchase_id")
);
CREATE UNIQUE INDEX IF NOT EXISTS "idx_donations_purchase_id" ON "donations" ("purchase_id", "payment_id");
CREATE TABLE IF NOT EXISTS "donations_payments" (
  "purchase_id" INTEGER NOT NULL DEFAULT 0,
  "mp_preference_id" TEXT DEFAULT NULL,
  "paypal_invoice_id" TEXT DEFAULT NULL,
  "qrcode" TEXT DEFAULT NULL,
  "link" TEXT DEFAULT NULL,
  PRIMARY KEY ("purchase_id")
);
CREATE TABLE IF NOT EXISTS "dungeon_cooldowns" (
  "dungeon_id" INTEGER NOT NULL,
  "player_id" INTEGER NOT NULL,
  "last_join" INTEGER NOT NULL,
  "next_join" INTEGER NOT NULL,
  "ip_address" TEXT DEFAULT NULL,
  "stage" INTEGER NOT NULL,
  PRIMARY KEY ("dungeon_id", "player_id")
);
CREATE TABLE IF NOT EXISTS "events_custom_data" (
  "event_name" TEXT NOT NULL,
  "status" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("event_name")
);
CREATE TABLE IF NOT EXISTS "fake_players" (
  "obj_id" INTEGER NOT NULL,
  "char_name" TEXT NOT NULL,
  "class_id" TEXT NOT NULL,
  "level" INTEGER DEFAULT 81,
  "x" INTEGER DEFAULT 0,
  "y" INTEGER DEFAULT 0,
  "z" INTEGER DEFAULT 0,
  "face" INTEGER DEFAULT 0,
  "hair_style" INTEGER DEFAULT 0,
  "hair_color" INTEGER DEFAULT 0,
  "sex" INTEGER DEFAULT 0,
  "archetype" TEXT DEFAULT 'Farmer',
  "persistent" INTEGER DEFAULT 0,
  "created_at" INTEGER DEFAULT 0,
  PRIMARY KEY ("obj_id")
);
CREATE TABLE IF NOT EXISTS "fishing_championship" (
  "player_name" TEXT NOT NULL,
  "fish_length" REAL NOT NULL,
  "rewarded" INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS "flyway_schema_history" (
  "installed_rank" INTEGER NOT NULL,
  "version" TEXT DEFAULT NULL,
  "description" TEXT NOT NULL,
  "type" TEXT NOT NULL,
  "script" TEXT NOT NULL,
  "checksum" INTEGER DEFAULT NULL,
  "installed_by" TEXT NOT NULL,
  "installed_on" TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "execution_time" INTEGER NOT NULL,
  "success" INTEGER NOT NULL,
  PRIMARY KEY ("installed_rank")
);
CREATE INDEX IF NOT EXISTS "idx_flyway_schema_history_flyway_schema_history_s_idx" ON "flyway_schema_history" ("success");
CREATE TABLE IF NOT EXISTS "games" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "idnr" INTEGER NOT NULL DEFAULT 0,
  "number1" INTEGER NOT NULL DEFAULT 0,
  "number2" INTEGER NOT NULL DEFAULT 0,
  "prize" INTEGER NOT NULL DEFAULT 0,
  "newprize" INTEGER NOT NULL DEFAULT 0,
  "prize1" INTEGER NOT NULL DEFAULT 0,
  "prize2" INTEGER NOT NULL DEFAULT 0,
  "prize3" INTEGER NOT NULL DEFAULT 0,
  "enddate" INTEGER NOT NULL DEFAULT 0,
  "finished" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("id", "idnr")
);
CREATE TABLE IF NOT EXISTS "gameservers" (
  "server_id" INTEGER NOT NULL DEFAULT 0,
  "hexid" TEXT NOT NULL DEFAULT '',
  "host" TEXT NOT NULL DEFAULT '',
  PRIMARY KEY ("server_id")
);
CREATE TABLE IF NOT EXISTS "grandboss_list" (
  "player_id" INTEGER NOT NULL,
  "zone" INTEGER NOT NULL,
  PRIMARY KEY ("player_id", "zone")
);
CREATE TABLE IF NOT EXISTS "heroes" (
  "char_id" INTEGER NOT NULL DEFAULT 0,
  "class_id" INTEGER NOT NULL DEFAULT 0,
  "count" INTEGER NOT NULL DEFAULT 0,
  "played" INTEGER NOT NULL DEFAULT 0,
  "active" INTEGER NOT NULL DEFAULT 0,
  "message" TEXT NOT NULL DEFAULT '',
  PRIMARY KEY ("char_id")
);
CREATE TABLE IF NOT EXISTS "heroes_diary" (
  "char_id" INTEGER NOT NULL,
  "time" INTEGER NOT NULL DEFAULT 0,
  "action" INTEGER NOT NULL DEFAULT 0,
  "param" INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS "idx_heroes_diary_char_id" ON "heroes_diary" ("char_id");
CREATE TABLE IF NOT EXISTS "hwid_bans" (
  "HWID" TEXT DEFAULT NULL,
  "HWIDSecond" TEXT DEFAULT NULL,
  "expiretime" INTEGER NOT NULL DEFAULT 0,
  "comments" TEXT DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS "idx_hwid_bans_HWID" ON "hwid_bans" ("HWID");
CREATE TABLE IF NOT EXISTS "hwid_extra_boxes" (
  "hwid" TEXT NOT NULL,
  "extra_boxes" INTEGER NOT NULL,
  PRIMARY KEY ("hwid")
);
CREATE TABLE IF NOT EXISTS "hwid_info" (
  "HWID" TEXT NOT NULL DEFAULT '',
  "Account" TEXT NOT NULL DEFAULT '',
  "PlayerID" INTEGER NOT NULL DEFAULT 0,
  "LockType" TEXT NOT NULL DEFAULT 'NONE',
  PRIMARY KEY ("HWID")
);
CREATE TABLE IF NOT EXISTS "items" (
  "owner_id" INTEGER DEFAULT NULL,
  "object_id" INTEGER NOT NULL DEFAULT 0,
  "item_id" INTEGER NOT NULL,
  "count" INTEGER NOT NULL DEFAULT 0,
  "enchant_level" INTEGER NOT NULL DEFAULT 0,
  "loc" TEXT DEFAULT NULL,
  "loc_data" INTEGER DEFAULT NULL,
  "custom_type1" INTEGER NOT NULL DEFAULT 0,
  "custom_type2" INTEGER NOT NULL DEFAULT 0,
  "mana_left" INTEGER NOT NULL DEFAULT -1,
  "time" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("object_id")
);
CREATE TABLE IF NOT EXISTS "items_delayed" (
  "owner_id" INTEGER NOT NULL,
  "item_id" INTEGER NOT NULL,
  "count" INTEGER NOT NULL DEFAULT 1,
  "enchant_level" INTEGER NOT NULL DEFAULT 0,
  "payment_status" INTEGER NOT NULL DEFAULT 0,
  "description" TEXT DEFAULT NULL
);
CREATE INDEX IF NOT EXISTS "idx_items_delayed_owner_id" ON "items_delayed" ("owner_id");
CREATE INDEX IF NOT EXISTS "idx_items_delayed_item_id" ON "items_delayed" ("item_id");
CREATE TABLE IF NOT EXISTS "items_on_ground" (
  "object_id" INTEGER NOT NULL DEFAULT 0,
  "item_id" INTEGER DEFAULT NULL,
  "count" INTEGER DEFAULT NULL,
  "enchant_level" INTEGER DEFAULT NULL,
  "x" INTEGER DEFAULT NULL,
  "y" INTEGER DEFAULT NULL,
  "z" INTEGER DEFAULT NULL,
  "time" INTEGER DEFAULT NULL,
  PRIMARY KEY ("object_id")
);
CREATE TABLE IF NOT EXISTS "mdt_bets" (
  "lane_id" INTEGER NOT NULL DEFAULT 0,
  "bet" INTEGER DEFAULT 0,
  PRIMARY KEY ("lane_id")
);
CREATE TABLE IF NOT EXISTS "mdt_history" (
  "race_id" INTEGER NOT NULL DEFAULT 0,
  "first" INTEGER DEFAULT 0,
  "second" INTEGER DEFAULT 0,
  "odd_rate" REAL DEFAULT 0.00,
  PRIMARY KEY ("race_id")
);
CREATE TABLE IF NOT EXISTS "mods_wedding" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "requesterId" INTEGER NOT NULL DEFAULT 0,
  "partnerId" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "olympiad_data" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "current_cycle" INTEGER NOT NULL DEFAULT 1,
  "period" TEXT NOT NULL DEFAULT 'COMPETITION',
  "olympiad_end" INTEGER NOT NULL DEFAULT 0,
  "validation_end" INTEGER NOT NULL DEFAULT 0,
  "next_weekly_change" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "olympiad_fights" (
  "charOneId" INTEGER NOT NULL,
  "charTwoId" INTEGER NOT NULL,
  "charOneClass" INTEGER NOT NULL DEFAULT 0,
  "charTwoClass" INTEGER NOT NULL DEFAULT 0,
  "winner" INTEGER NOT NULL DEFAULT 0,
  "start" INTEGER NOT NULL DEFAULT 0,
  "time" INTEGER NOT NULL DEFAULT 0,
  "classed" INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS "idx_olympiad_fights_charOneId" ON "olympiad_fights" ("charOneId");
CREATE INDEX IF NOT EXISTS "idx_olympiad_fights_charTwoId" ON "olympiad_fights" ("charTwoId");
CREATE TABLE IF NOT EXISTS "olympiad_nobles" (
  "char_id" INTEGER NOT NULL DEFAULT 0,
  "class_id" INTEGER NOT NULL DEFAULT 0,
  "olympiad_points" INTEGER NOT NULL DEFAULT 0,
  "competitions_done" INTEGER NOT NULL DEFAULT 0,
  "competitions_won" INTEGER NOT NULL DEFAULT 0,
  "competitions_lost" INTEGER NOT NULL DEFAULT 0,
  "competitions_drawn" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_id")
);
CREATE TABLE IF NOT EXISTS "olympiad_nobles_eom" (
  "char_id" INTEGER NOT NULL DEFAULT 0,
  "class_id" INTEGER NOT NULL DEFAULT 0,
  "olympiad_points" INTEGER NOT NULL DEFAULT 0,
  "competitions_done" INTEGER NOT NULL DEFAULT 0,
  "competitions_won" INTEGER NOT NULL DEFAULT 0,
  "competitions_lost" INTEGER NOT NULL DEFAULT 0,
  "competitions_drawn" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_id")
);
CREATE TABLE IF NOT EXISTS "petition" (
  "oid" INTEGER NOT NULL DEFAULT 0,
  "type" TEXT NOT NULL,
  "petitioner_oid" INTEGER NOT NULL DEFAULT 0,
  "submit_date" INTEGER NOT NULL DEFAULT 0,
  "content" TEXT NOT NULL,
  "is_unread" INTEGER NOT NULL DEFAULT 1,
  "state" TEXT NOT NULL,
  "rate" TEXT NOT NULL,
  "feedback" TEXT NOT NULL,
  "responders" TEXT NOT NULL,
  PRIMARY KEY ("oid")
);
CREATE TABLE IF NOT EXISTS "petition_message" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "petition_oid" INTEGER NOT NULL DEFAULT 0,
  "player_oid" INTEGER NOT NULL DEFAULT 0,
  "type" TEXT NOT NULL,
  "player_name" TEXT NOT NULL,
  "content" TEXT NOT NULL,
  PRIMARY KEY ("id", "petition_oid")
);
CREATE TABLE IF NOT EXISTS "pets" (
  "item_obj_id" INTEGER NOT NULL DEFAULT 0,
  "name" TEXT DEFAULT NULL,
  "level" INTEGER DEFAULT NULL,
  "curHp" INTEGER DEFAULT NULL,
  "curMp" INTEGER DEFAULT NULL,
  "exp" INTEGER DEFAULT NULL,
  "sp" INTEGER DEFAULT NULL,
  "fed" INTEGER DEFAULT NULL,
  PRIMARY KEY ("item_obj_id")
);
CREATE TABLE IF NOT EXISTS "player_droplist_config" (
  "player_id" INTEGER NOT NULL,
  "item_id" INTEGER NOT NULL,
  PRIMARY KEY ("player_id", "item_id")
);
CREATE TABLE IF NOT EXISTS "player_emails" (
  "id" INTEGER NOT NULL,
  "sender_id" INTEGER NOT NULL,
  "target_id" INTEGER NOT NULL,
  "email_id" INTEGER NOT NULL,
  "item_object_id" INTEGER NOT NULL,
  "item_id" INTEGER NOT NULL,
  "count" INTEGER NOT NULL,
  "enchant_level" INTEGER NOT NULL,
  "is_augmented" INTEGER NOT NULL DEFAULT 0,
  "augment_id" INTEGER DEFAULT NULL,
  "is_paid" INTEGER NOT NULL DEFAULT 0,
  "payment_item_id" INTEGER DEFAULT NULL,
  "payment_item_count" INTEGER DEFAULT NULL,
  "status" TEXT DEFAULT 'PENDING',
  "expiration_time" INTEGER NOT NULL,
  "created_time" INTEGER NOT NULL,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "rainbowsprings_attacker_list" (
  "clanId" INTEGER NOT NULL DEFAULT 0,
  "war_decrees_count" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("clanId")
);
CREATE TABLE IF NOT EXISTS "server_memo" (
  "var" TEXT NOT NULL DEFAULT '',
  "value" TEXT NOT NULL DEFAULT '',
  PRIMARY KEY ("var")
);
CREATE TABLE IF NOT EXISTS "seven_signs" (
  "char_obj_id" INTEGER NOT NULL DEFAULT 0,
  "cabal" TEXT NOT NULL DEFAULT 'NORMAL',
  "seal" TEXT NOT NULL DEFAULT 'NONE',
  "red_stones" INTEGER NOT NULL DEFAULT 0,
  "green_stones" INTEGER NOT NULL DEFAULT 0,
  "blue_stones" INTEGER NOT NULL DEFAULT 0,
  "ancient_adena_amount" INTEGER NOT NULL DEFAULT 0,
  "contribution_score" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("char_obj_id")
);
CREATE TABLE IF NOT EXISTS "seven_signs_festival" (
  "festivalId" INTEGER NOT NULL DEFAULT 0,
  "cabal" TEXT NOT NULL DEFAULT '',
  "cycle" INTEGER NOT NULL DEFAULT 0,
  "date" INTEGER DEFAULT 0,
  "score" INTEGER NOT NULL DEFAULT 0,
  "members" TEXT NOT NULL DEFAULT '',
  PRIMARY KEY ("festivalId", "cabal", "cycle")
);
CREATE TABLE IF NOT EXISTS "seven_signs_status" (
  "id" INTEGER NOT NULL DEFAULT 0,
  "current_cycle" INTEGER NOT NULL DEFAULT 1,
  "festival_cycle" INTEGER NOT NULL DEFAULT 1,
  "active_period" TEXT NOT NULL DEFAULT 'COMPETITION',
  "date" INTEGER NOT NULL DEFAULT 0,
  "previous_winner" TEXT NOT NULL DEFAULT 'NORMAL',
  "dawn_stone_score" INTEGER NOT NULL DEFAULT 0,
  "dawn_festival_score" INTEGER NOT NULL DEFAULT 0,
  "dusk_stone_score" INTEGER NOT NULL DEFAULT 0,
  "dusk_festival_score" INTEGER NOT NULL DEFAULT 0,
  "avarice_owner" TEXT NOT NULL DEFAULT 'NORMAL',
  "gnosis_owner" TEXT NOT NULL DEFAULT 'NORMAL',
  "strife_owner" TEXT NOT NULL DEFAULT 'NORMAL',
  "avarice_dawn_score" INTEGER NOT NULL DEFAULT 0,
  "gnosis_dawn_score" INTEGER NOT NULL DEFAULT 0,
  "strife_dawn_score" INTEGER NOT NULL DEFAULT 0,
  "avarice_dusk_score" INTEGER NOT NULL DEFAULT 0,
  "gnosis_dusk_score" INTEGER NOT NULL DEFAULT 0,
  "strife_dusk_score" INTEGER NOT NULL DEFAULT 0,
  "accumulated_bonus0" INTEGER NOT NULL DEFAULT 0,
  "accumulated_bonus1" INTEGER NOT NULL DEFAULT 0,
  "accumulated_bonus2" INTEGER NOT NULL DEFAULT 0,
  "accumulated_bonus3" INTEGER NOT NULL DEFAULT 0,
  "accumulated_bonus4" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("id")
);
CREATE TABLE IF NOT EXISTS "siege_clans" (
  "castle_id" INTEGER NOT NULL DEFAULT 0,
  "clan_id" INTEGER NOT NULL DEFAULT 0,
  "type" TEXT DEFAULT 'PENDING',
  PRIMARY KEY ("castle_id", "clan_id")
);
CREATE TABLE IF NOT EXISTS "spawn_data" (
  "name" TEXT NOT NULL,
  "status" INTEGER NOT NULL,
  "current_hp" INTEGER NOT NULL,
  "current_mp" INTEGER NOT NULL,
  "loc_x" INTEGER NOT NULL DEFAULT 0,
  "loc_y" INTEGER NOT NULL DEFAULT 0,
  "loc_z" INTEGER NOT NULL DEFAULT 0,
  "heading" INTEGER NOT NULL DEFAULT 0,
  "db_value" INTEGER NOT NULL DEFAULT 0,
  "respawn_time" INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY ("name")
);
COMMIT;
PRAGMA foreign_keys=ON;
