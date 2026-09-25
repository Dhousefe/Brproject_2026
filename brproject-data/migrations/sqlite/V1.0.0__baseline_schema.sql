-- BrProject Phase 5 Flyway baseline — SQLite variant
-- Targets the tables required by the L2J server boot sequence.
-- MySQL/MariaDB-specific syntax (ENGINE=, CHARSET=, AUTO_INCREMENT, ON UPDATE) is removed.
-- SQLite type affinity is used; INTEGER is 64-bit per SQLite docs.

-- ========== accounts ==========
CREATE TABLE IF NOT EXISTS accounts (
  login TEXT NOT NULL DEFAULT '',
  password TEXT NOT NULL DEFAULT '',
  last_active INTEGER NOT NULL DEFAULT 0,
  access_level INTEGER NOT NULL DEFAULT 0,
  last_server INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (login)
);

-- ========== account_premium ==========
CREATE TABLE IF NOT EXISTS account_premium (
  account_name TEXT NOT NULL DEFAULT '',
  premium_service INTEGER NOT NULL DEFAULT 0,
  enddate REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (account_name)
);

-- ========== gameservers ==========
CREATE TABLE IF NOT EXISTS gameservers (
  server_id INTEGER NOT NULL DEFAULT 0,
  hexid TEXT NOT NULL DEFAULT '',
  host TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (server_id)
);

-- ========== characters ==========
CREATE TABLE IF NOT EXISTS characters (
  account_name TEXT,
  obj_Id INTEGER NOT NULL DEFAULT 0,
  char_name TEXT NOT NULL,
  level INTEGER,
  maxHp INTEGER,
  curHp INTEGER,
  maxCp INTEGER,
  curCp INTEGER,
  maxMp INTEGER,
  curMp INTEGER,
  face INTEGER,
  hairStyle INTEGER,
  hairColor INTEGER,
  sex INTEGER,
  heading INTEGER,
  x INTEGER,
  y INTEGER,
  z INTEGER,
  exp INTEGER NOT NULL DEFAULT 0,
  expBeforeDeath INTEGER NOT NULL DEFAULT 0,
  sp INTEGER NOT NULL DEFAULT 0,
  karma INTEGER,
  pvpkills INTEGER,
  pkkills INTEGER,
  clanid INTEGER,
  race INTEGER,
  classid INTEGER,
  base_class INTEGER NOT NULL DEFAULT 0,
  deletetime INTEGER,
  title TEXT,
  rec_have INTEGER NOT NULL DEFAULT 0,
  rec_left INTEGER NOT NULL DEFAULT 0,
  accesslevel INTEGER NOT NULL DEFAULT 0,
  online INTEGER,
  onlinetime INTEGER,
  lastAccess INTEGER,
  wantspeace INTEGER NOT NULL DEFAULT 0,
  isin7sdungeon INTEGER NOT NULL DEFAULT 0,
  punish_level INTEGER NOT NULL DEFAULT 0,
  punish_timer INTEGER NOT NULL DEFAULT 0,
  power_grade INTEGER,
  nobless INTEGER NOT NULL DEFAULT 0,
  hero INTEGER NOT NULL DEFAULT 0,
  subpledge INTEGER NOT NULL DEFAULT 0,
  lvl_joined_academy INTEGER NOT NULL DEFAULT 0,
  apprentice INTEGER NOT NULL DEFAULT 0,
  sponsor INTEGER NOT NULL DEFAULT 0,
  varka_ketra_ally INTEGER NOT NULL DEFAULT 0,
  clan_join_expiry_time INTEGER NOT NULL DEFAULT 0,
  clan_create_expiry_time INTEGER NOT NULL DEFAULT 0,
  death_penalty_level INTEGER NOT NULL DEFAULT 0,
  herountil INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (obj_Id)
);
CREATE INDEX IF NOT EXISTS idx_characters_clanid ON characters(clanid);

-- ========== character_recommends ==========
CREATE TABLE IF NOT EXISTS character_recommends (
  char_id INTEGER NOT NULL DEFAULT 0,
  target_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_id, target_id)
);

-- ========== character_skills_save ==========
CREATE TABLE IF NOT EXISTS character_skills_save (
  char_obj_id INTEGER NOT NULL DEFAULT 0,
  skill_id INTEGER NOT NULL DEFAULT 0,
  skill_level INTEGER NOT NULL DEFAULT 1,
  effect_count INTEGER NOT NULL DEFAULT 0,
  effect_cur_time INTEGER NOT NULL DEFAULT 0,
  reuse_delay INTEGER NOT NULL DEFAULT 0,
  systime INTEGER NOT NULL DEFAULT 0,
  restore_type INTEGER NOT NULL DEFAULT 0,
  class_index INTEGER NOT NULL DEFAULT 0,
  buff_index INTEGER NOT NULL DEFAULT 0,
  npc INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_obj_id, skill_id, skill_level, class_index)
);

-- ========== character_quests ==========
CREATE TABLE IF NOT EXISTS character_quests (
  charId INTEGER NOT NULL DEFAULT 0,
  name TEXT NOT NULL DEFAULT '',
  var TEXT NOT NULL DEFAULT '',
  value TEXT,
  PRIMARY KEY (charId, name, var)
);

-- ========== character_mission ==========
CREATE TABLE IF NOT EXISTS character_mission (
  object_id INTEGER NOT NULL,
  type TEXT NOT NULL,
  level INTEGER NOT NULL DEFAULT 0,
  value INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (object_id, type)
);
CREATE INDEX IF NOT EXISTS idx_character_mission_object_id ON character_mission(object_id);

-- ========== character_hennas ==========
CREATE TABLE IF NOT EXISTS character_hennas (
  char_obj_id INTEGER NOT NULL DEFAULT 0,
  symbol_id INTEGER,
  slot INTEGER NOT NULL DEFAULT 0,
  class_index INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_obj_id, slot, class_index)
);

-- ========== character_subclasses ==========
CREATE TABLE IF NOT EXISTS character_subclasses (
  char_obj_id REAL NOT NULL DEFAULT 0,
  class_id INTEGER NOT NULL DEFAULT 0,
  exp REAL NOT NULL DEFAULT 0,
  sp REAL NOT NULL DEFAULT 0,
  level INTEGER NOT NULL DEFAULT 40,
  class_index INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_obj_id, class_id)
);

-- ========== character_shortcuts ==========
CREATE TABLE IF NOT EXISTS character_shortcuts (
  char_obj_id INTEGER NOT NULL DEFAULT 0,
  slot INTEGER NOT NULL DEFAULT 0,
  page INTEGER NOT NULL DEFAULT 0,
  type TEXT NOT NULL DEFAULT 'NONE',
  id INTEGER NOT NULL DEFAULT 0,
  level INTEGER NOT NULL DEFAULT 0,
  class_index INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_obj_id, slot, page, class_index)
);
CREATE INDEX IF NOT EXISTS idx_character_shortcuts_id ON character_shortcuts(id);

-- ========== character_memo ==========
CREATE TABLE IF NOT EXISTS character_memo (
  charId INTEGER NOT NULL,
  var TEXT NOT NULL,
  val TEXT NOT NULL,
  PRIMARY KEY (charId, var)
);

-- ========== character_recipebook ==========
CREATE TABLE IF NOT EXISTS character_recipebook (
  charId INTEGER NOT NULL DEFAULT 0,
  recipeId INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (charId, recipeId)
);

-- ========== character_raid_points ==========
CREATE TABLE IF NOT EXISTS character_raid_points (
  char_id INTEGER NOT NULL DEFAULT 0,
  boss_id INTEGER NOT NULL DEFAULT 0,
  points INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_id, boss_id)
);

-- ========== character_macroses ==========
CREATE TABLE IF NOT EXISTS character_macroses (
  char_obj_id INTEGER NOT NULL DEFAULT 0,
  id INTEGER NOT NULL DEFAULT 0,
  icon INTEGER,
  name TEXT,
  descr TEXT,
  acronym TEXT,
  commands TEXT,
  PRIMARY KEY (char_obj_id, id)
);

-- ========== character_data ==========
CREATE TABLE IF NOT EXISTS character_data (
  charId INTEGER NOT NULL,
  valueName TEXT NOT NULL,
  valueData TEXT,
  PRIMARY KEY (charId, valueName)
);

-- ========== character_relations ==========
CREATE TABLE IF NOT EXISTS character_relations (
  char_id INTEGER NOT NULL DEFAULT 0,
  friend_id INTEGER NOT NULL DEFAULT 0,
  relation INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_id, friend_id)
);

-- ========== character_skills ==========
CREATE TABLE IF NOT EXISTS character_skills (
  char_obj_id INTEGER NOT NULL DEFAULT 0,
  skill_id INTEGER NOT NULL DEFAULT 0,
  skill_level INTEGER NOT NULL DEFAULT 1,
  class_index INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_obj_id, skill_id, class_index)
);

-- ========== items ==========
CREATE TABLE IF NOT EXISTS items (
  owner_id INTEGER,
  object_id INTEGER NOT NULL DEFAULT 0,
  item_id INTEGER NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  enchant_level INTEGER NOT NULL DEFAULT 0,
  loc TEXT,
  loc_data INTEGER,
  custom_type1 INTEGER NOT NULL DEFAULT 0,
  custom_type2 INTEGER NOT NULL DEFAULT 0,
  mana_left INTEGER NOT NULL DEFAULT -1,
  time INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (object_id)
);

-- ========== items_delayed ==========
CREATE TABLE IF NOT EXISTS items_delayed (
  owner_id INTEGER NOT NULL,
  item_id INTEGER NOT NULL,
  count INTEGER NOT NULL DEFAULT 1,
  enchant_level INTEGER NOT NULL DEFAULT 0,
  payment_status INTEGER NOT NULL DEFAULT 0,
  description TEXT,
  PRIMARY KEY (owner_id, item_id, count, payment_status)
);
CREATE INDEX IF NOT EXISTS idx_items_delayed_owner_id ON items_delayed(owner_id);

-- ========== items_on_ground ==========
CREATE TABLE IF NOT EXISTS items_on_ground (
  object_id INTEGER NOT NULL DEFAULT 0,
  item_id INTEGER,
  count INTEGER,
  enchant_level INTEGER,
  x INTEGER,
  y INTEGER,
  z INTEGER,
  time REAL,
  PRIMARY KEY (object_id)
);

-- ========== pets ==========
CREATE TABLE IF NOT EXISTS pets (
  item_obj_id REAL NOT NULL DEFAULT 0,
  name TEXT,
  level REAL,
  curHp REAL,
  curMp REAL,
  exp REAL,
  sp REAL,
  fed REAL,
  PRIMARY KEY (item_obj_id)
);

-- ========== augmentations ==========
CREATE TABLE IF NOT EXISTS augmentations (
  item_oid INTEGER NOT NULL DEFAULT 0,
  attributes INTEGER NOT NULL DEFAULT -1,
  skill_id INTEGER NOT NULL DEFAULT -1,
  skill_level INTEGER NOT NULL DEFAULT -1,
  PRIMARY KEY (item_oid)
);

-- ========== cursed_weapons ==========
CREATE TABLE IF NOT EXISTS cursed_weapons (
  itemId INTEGER,
  playerId INTEGER DEFAULT 0,
  playerKarma INTEGER DEFAULT 0,
  playerPkKills INTEGER DEFAULT 0,
  nbKills INTEGER DEFAULT 0,
  currentStage INTEGER DEFAULT 0,
  numberBeforeNextStage INTEGER DEFAULT 0,
  hungryTime INTEGER DEFAULT 0,
  endTime REAL DEFAULT 0,
  PRIMARY KEY (itemId)
);

-- ========== clan_data ==========
CREATE TABLE IF NOT EXISTS clan_data (
  clan_id INTEGER NOT NULL DEFAULT 0,
  clan_name TEXT,
  clan_level INTEGER NOT NULL DEFAULT 0,
  reputation_score INTEGER NOT NULL DEFAULT 0,
  hasCastle INTEGER NOT NULL DEFAULT 0,
  ally_id INTEGER NOT NULL DEFAULT 0,
  ally_name TEXT,
  leader_id INTEGER NOT NULL DEFAULT 0,
  new_leader_id INTEGER NOT NULL DEFAULT 0,
  crest_id INTEGER NOT NULL DEFAULT 0,
  crest_large_id INTEGER NOT NULL DEFAULT 0,
  ally_crest_id INTEGER NOT NULL DEFAULT 0,
  auction_bid_at INTEGER NOT NULL DEFAULT 0,
  ally_penalty_expiry_time INTEGER NOT NULL DEFAULT 0,
  ally_penalty_type INTEGER NOT NULL DEFAULT 0,
  char_penalty_expiry_time INTEGER NOT NULL DEFAULT 0,
  dissolving_expiry_time INTEGER NOT NULL DEFAULT 0,
  enabled INTEGER NOT NULL DEFAULT 0,
  notice TEXT,
  introduction TEXT,
  graduates TEXT,
  PRIMARY KEY (clan_id)
);
CREATE INDEX IF NOT EXISTS idx_clan_data_leader_id ON clan_data(leader_id);
CREATE INDEX IF NOT EXISTS idx_clan_data_ally_id ON clan_data(ally_id);

-- ========== clanhall_flagwar_attackers ==========
CREATE TABLE IF NOT EXISTS clanhall_flagwar_attackers (
  clanhall_id INTEGER NOT NULL DEFAULT 0,
  flag INTEGER NOT NULL DEFAULT 0,
  npc INTEGER NOT NULL DEFAULT 0,
  clan_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (flag)
);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_attackers_hall_id ON clanhall_flagwar_attackers(clanhall_id);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_attackers_clan_id ON clanhall_flagwar_attackers(clan_id);

-- ========== clan_privs ==========
CREATE TABLE IF NOT EXISTS clan_privs (
  clan_id INTEGER NOT NULL DEFAULT 0,
  ranking INTEGER NOT NULL DEFAULT 0,
  privs INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id, ranking)
);

-- ========== clan_skills ==========
CREATE TABLE IF NOT EXISTS clan_skills (
  clan_id INTEGER NOT NULL DEFAULT 0,
  skill_id INTEGER NOT NULL DEFAULT 0,
  skill_level INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id, skill_id)
);

-- ========== clanhall_flagwar_members ==========
CREATE TABLE IF NOT EXISTS clanhall_flagwar_members (
  clanhall_id INTEGER NOT NULL DEFAULT 0,
  clan_id INTEGER NOT NULL DEFAULT 0,
  object_id INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_members_hall_id ON clanhall_flagwar_members(clanhall_id);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_members_clan_id ON clanhall_flagwar_members(clan_id);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_members_object_id ON clanhall_flagwar_members(object_id);

-- ========== clan_subpledges ==========
CREATE TABLE IF NOT EXISTS clan_subpledges (
  clan_id INTEGER NOT NULL DEFAULT 0,
  sub_pledge_id INTEGER NOT NULL DEFAULT 0,
  name TEXT,
  leader_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clan_id, sub_pledge_id)
);

-- ========== clanhall_flagwar_owner_npcs ==========
CREATE TABLE IF NOT EXISTS clanhall_flagwar_owner_npcs (
  clanhall_id INTEGER NOT NULL DEFAULT 0,
  npc_id INTEGER NOT NULL DEFAULT 0,
  clan_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clanhall_id)
);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_owner_npcs_npc_id ON clanhall_flagwar_owner_npcs(npc_id);
CREATE INDEX IF NOT EXISTS idx_clanhall_flagwar_owner_npcs_clan_id ON clanhall_flagwar_owner_npcs(clan_id);

-- ========== clan_wars ==========
CREATE TABLE IF NOT EXISTS clan_wars (
  clan1 TEXT NOT NULL DEFAULT '',
  clan2 TEXT NOT NULL DEFAULT '',
  expiry_time REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (clan1, clan2)
);

-- ========== clanhall_siege_attackers ==========
CREATE TABLE IF NOT EXISTS clanhall_siege_attackers (
  clanhall_id INTEGER NOT NULL DEFAULT 0,
  attacker_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clanhall_id, attacker_id)
);

-- ========== clanhall ==========
CREATE TABLE IF NOT EXISTS clanhall (
  id INTEGER NOT NULL DEFAULT 0,
  ownerId INTEGER NOT NULL DEFAULT 0,
  paidUntil INTEGER NOT NULL DEFAULT 0,
  paid INTEGER NOT NULL DEFAULT 0,
  sellerBid INTEGER NOT NULL DEFAULT 0,
  sellerName TEXT NOT NULL DEFAULT '',
  sellerClanName TEXT NOT NULL DEFAULT '',
  endDate INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

INSERT OR IGNORE INTO clanhall (id, ownerId, paidUntil, paid, sellerBid, sellerName, sellerClanName, endDate) VALUES
  (21, 0, 0, 0, 0, '', '', 0),
  (22, 0, 0, 0, 0, '', '', 0),
  (23, 0, 0, 0, 0, '', '', 0),
  (24, 0, 0, 0, 0, '', '', 0),
  (25, 0, 0, 0, 0, '', '', 0),
  (26, 0, 0, 0, 0, '', '', 0),
  (27, 0, 0, 0, 0, '', '', 0),
  (28, 0, 0, 0, 0, '', '', 0),
  (29, 0, 0, 0, 0, '', '', 0),
  (30, 0, 0, 0, 0, '', '', 0),
  (31, 0, 0, 0, 0, '', '', 0),
  (32, 0, 0, 0, 0, '', '', 0),
  (33, 0, 0, 0, 0, '', '', 0),
  (34, 0, 0, 0, 0, '', '', 0),
  (35, 0, 0, 0, 0, '', '', 0),
  (36, 0, 0, 0, 0, '', '', 0),
  (37, 0, 0, 0, 0, '', '', 0),
  (38, 0, 0, 0, 0, '', '', 0),
  (39, 0, 0, 0, 0, '', '', 0),
  (40, 0, 0, 0, 0, '', '', 0),
  (41, 0, 0, 0, 0, '', '', 0),
  (42, 0, 0, 0, 0, '', '', 0),
  (43, 0, 0, 0, 0, '', '', 0),
  (44, 0, 0, 0, 0, '', '', 0),
  (45, 0, 0, 0, 0, '', '', 0),
  (46, 0, 0, 0, 0, '', '', 0),
  (47, 0, 0, 0, 0, '', '', 0),
  (48, 0, 0, 0, 0, '', '', 0),
  (49, 0, 0, 0, 0, '', '', 0),
  (50, 0, 0, 0, 0, '', '', 0),
  (51, 0, 0, 0, 0, '', '', 0),
  (52, 0, 0, 0, 0, '', '', 0),
  (53, 0, 0, 0, 0, '', '', 0),
  (54, 0, 0, 0, 0, '', '', 0),
  (55, 0, 0, 0, 0, '', '', 0),
  (56, 0, 0, 0, 0, '', '', 0),
  (57, 0, 0, 0, 0, '', '', 0),
  (58, 0, 0, 0, 0, '', '', 0),
  (59, 0, 0, 0, 0, '', '', 0),
  (60, 0, 0, 0, 0, '', '', 0),
  (61, 0, 0, 0, 0, '', '', 0),
  (62, 0, 0, 0, 0, '', '', 0),
  (63, 0, 0, 0, 0, '', '', 0),
  (64, 0, 0, 0, 0, '', '', 0);

-- ========== clanhall_functions ==========
CREATE TABLE IF NOT EXISTS clanhall_functions (
  hall_id INTEGER NOT NULL DEFAULT 0,
  type INTEGER NOT NULL DEFAULT 0,
  lvl INTEGER NOT NULL DEFAULT 0,
  lease INTEGER NOT NULL DEFAULT 0,
  rate REAL NOT NULL DEFAULT 0,
  endTime REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (hall_id, type)
);

-- ========== castle_manor_procure ==========
CREATE TABLE IF NOT EXISTS castle_manor_procure (
  castle_id INTEGER NOT NULL DEFAULT 0,
  crop_id INTEGER NOT NULL DEFAULT 0,
  amount INTEGER NOT NULL DEFAULT 0,
  start_amount INTEGER NOT NULL DEFAULT 0,
  price INTEGER NOT NULL DEFAULT 0,
  reward_type INTEGER NOT NULL DEFAULT 0,
  next_period INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (castle_id, crop_id, next_period)
);

-- ========== castle_doorupgrade ==========
CREATE TABLE IF NOT EXISTS castle_doorupgrade (
  doorId INTEGER NOT NULL DEFAULT 0,
  hp INTEGER NOT NULL DEFAULT 0,
  castleId INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (doorId)
);

-- ========== castle_trapupgrade ==========
CREATE TABLE IF NOT EXISTS castle_trapupgrade (
  castleId INTEGER NOT NULL DEFAULT 0,
  towerIndex INTEGER NOT NULL DEFAULT 0,
  level INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (towerIndex, castleId)
);

-- ========== castle ==========
-- Original used ENUM('true','false') for regTimeOver — replaced with TEXT + CHECK.
CREATE TABLE IF NOT EXISTS castle (
  id INTEGER NOT NULL DEFAULT 0,
  currentTaxPercent INTEGER NOT NULL DEFAULT 0,
  nextTaxPercent INTEGER NOT NULL DEFAULT 0,
  treasury INTEGER NOT NULL DEFAULT 0,
  taxRevenue INTEGER NOT NULL DEFAULT 0,
  seedIncome INTEGER NOT NULL DEFAULT 0,
  siegeDate REAL NOT NULL DEFAULT 0,
  regTimeOver TEXT NOT NULL DEFAULT 'true' CHECK (regTimeOver IN ('true', 'false')),
  certificates INTEGER NOT NULL DEFAULT 300,
  PRIMARY KEY (id)
);

INSERT OR IGNORE INTO castle (id, currentTaxPercent, nextTaxPercent, treasury, taxRevenue, seedIncome, siegeDate, regTimeOver, certificates) VALUES
  (1, 15, 15, 0, 0, 0, 0, 'true', 300),
  (2, 15, 15, 0, 0, 0, 0, 'true', 300),
  (3, 15, 15, 0, 0, 0, 0, 'true', 300),
  (4, 15, 15, 0, 0, 0, 0, 'true', 300),
  (5, 15, 15, 0, 0, 0, 0, 'true', 300),
  (6, 15, 15, 0, 0, 0, 0, 'true', 300),
  (7, 15, 15, 0, 0, 0, 0, 'true', 300),
  (8, 15, 15, 0, 0, 0, 0, 'true', 300),
  (9, 15, 15, 0, 0, 0, 0, 'true', 300);

-- ========== castle_manor_production ==========
CREATE TABLE IF NOT EXISTS castle_manor_production (
  castle_id INTEGER NOT NULL DEFAULT 0,
  seed_id INTEGER NOT NULL DEFAULT 0,
  amount INTEGER NOT NULL DEFAULT 0,
  start_amount INTEGER NOT NULL DEFAULT 0,
  price INTEGER NOT NULL DEFAULT 0,
  next_period INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (castle_id, seed_id, next_period)
);

-- ========== castle_functions ==========
CREATE TABLE IF NOT EXISTS castle_functions (
  castle_id INTEGER NOT NULL DEFAULT 0,
  type INTEGER NOT NULL DEFAULT 0,
  lvl INTEGER NOT NULL DEFAULT 0,
  lease INTEGER NOT NULL DEFAULT 0,
  rate REAL NOT NULL DEFAULT 0,
  endTime INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (castle_id, type)
);

-- ========== siege_clans ==========
CREATE TABLE IF NOT EXISTS siege_clans (
  castle_id INTEGER NOT NULL DEFAULT 0,
  clan_id INTEGER NOT NULL DEFAULT 0,
  type TEXT DEFAULT 'PENDING',
  PRIMARY KEY (castle_id, clan_id)
);

-- ========== spawn_data ==========
CREATE TABLE IF NOT EXISTS spawn_data (
  name TEXT NOT NULL,
  status INTEGER NOT NULL,
  current_hp INTEGER NOT NULL,
  current_mp INTEGER NOT NULL,
  loc_x INTEGER NOT NULL DEFAULT 0,
  loc_y INTEGER NOT NULL DEFAULT 0,
  loc_z INTEGER NOT NULL DEFAULT 0,
  heading INTEGER NOT NULL DEFAULT 0,
  db_value INTEGER NOT NULL DEFAULT 0,
  respawn_time INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (name)
);

-- ========== olympiad_nobles_eom ==========
CREATE TABLE IF NOT EXISTS olympiad_nobles_eom (
  char_id INTEGER NOT NULL DEFAULT 0,
  class_id INTEGER NOT NULL DEFAULT 0,
  olympiad_points INTEGER NOT NULL DEFAULT 0,
  competitions_done INTEGER NOT NULL DEFAULT 0,
  competitions_won INTEGER NOT NULL DEFAULT 0,
  competitions_lost INTEGER NOT NULL DEFAULT 0,
  competitions_drawn INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_id)
);

-- ========== olympiad_fights ==========
CREATE TABLE IF NOT EXISTS olympiad_fights (
  charOneId INTEGER NOT NULL,
  charTwoId INTEGER NOT NULL,
  charOneClass INTEGER NOT NULL DEFAULT 0,
  charTwoClass INTEGER NOT NULL DEFAULT 0,
  winner INTEGER NOT NULL DEFAULT 0,
  start INTEGER NOT NULL DEFAULT 0,
  time INTEGER NOT NULL DEFAULT 0,
  classed INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_olympiad_fights_charOneId ON olympiad_fights(charOneId);
CREATE INDEX IF NOT EXISTS idx_olympiad_fights_charTwoId ON olympiad_fights(charTwoId);

-- ========== olympiad_data ==========
CREATE TABLE IF NOT EXISTS olympiad_data (
  id INTEGER NOT NULL DEFAULT 0,
  current_cycle INTEGER NOT NULL DEFAULT 1,
  period TEXT NOT NULL DEFAULT 'COMPETITION',
  olympiad_end INTEGER NOT NULL DEFAULT 0,
  validation_end INTEGER NOT NULL DEFAULT 0,
  next_weekly_change INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ========== olympiad_nobles ==========
CREATE TABLE IF NOT EXISTS olympiad_nobles (
  char_id INTEGER NOT NULL DEFAULT 0,
  class_id INTEGER NOT NULL DEFAULT 0,
  olympiad_points INTEGER NOT NULL DEFAULT 0,
  competitions_done INTEGER NOT NULL DEFAULT 0,
  competitions_won INTEGER NOT NULL DEFAULT 0,
  competitions_lost INTEGER NOT NULL DEFAULT 0,
  competitions_drawn INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (char_id)
);

-- ========== seven_signs_festival ==========
CREATE TABLE IF NOT EXISTS seven_signs_festival (
  festivalId INTEGER NOT NULL DEFAULT 0,
  cabal TEXT NOT NULL DEFAULT '',
  cycle INTEGER NOT NULL DEFAULT 0,
  date INTEGER DEFAULT 0,
  score INTEGER NOT NULL DEFAULT 0,
  members TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (festivalId, cabal, cycle)
);

INSERT OR IGNORE INTO seven_signs_festival (festivalId, cabal, cycle, date, score, members) VALUES
  (0, 'DAWN', 1, 0, 0, ''),
  (1, 'DAWN', 1, 0, 0, ''),
  (2, 'DAWN', 1, 0, 0, ''),
  (3, 'DAWN', 1, 0, 0, ''),
  (4, 'DAWN', 1, 0, 0, ''),
  (0, 'DUSK', 1, 0, 0, ''),
  (1, 'DUSK', 1, 0, 0, ''),
  (2, 'DUSK', 1, 0, 0, ''),
  (3, 'DUSK', 1, 0, 0, ''),
  (4, 'DUSK', 1, 0, 0, '');

-- ========== seven_signs_status ==========
CREATE TABLE IF NOT EXISTS seven_signs_status (
  id INTEGER NOT NULL DEFAULT 0,
  current_cycle INTEGER NOT NULL DEFAULT 1,
  festival_cycle INTEGER NOT NULL DEFAULT 1,
  active_period TEXT NOT NULL DEFAULT 'COMPETITION',
  date INTEGER NOT NULL DEFAULT 0,
  previous_winner TEXT NOT NULL DEFAULT 'NORMAL',
  dawn_stone_score REAL NOT NULL DEFAULT 0,
  dawn_festival_score INTEGER NOT NULL DEFAULT 0,
  dusk_stone_score REAL NOT NULL DEFAULT 0,
  dusk_festival_score INTEGER NOT NULL DEFAULT 0,
  avarice_owner TEXT NOT NULL DEFAULT 'NORMAL',
  gnosis_owner TEXT NOT NULL DEFAULT 'NORMAL',
  strife_owner TEXT NOT NULL DEFAULT 'NORMAL',
  avarice_dawn_score INTEGER NOT NULL DEFAULT 0,
  gnosis_dawn_score INTEGER NOT NULL DEFAULT 0,
  strife_dawn_score INTEGER NOT NULL DEFAULT 0,
  avarice_dusk_score INTEGER NOT NULL DEFAULT 0,
  gnosis_dusk_score INTEGER NOT NULL DEFAULT 0,
  strife_dusk_score INTEGER NOT NULL DEFAULT 0,
  accumulated_bonus0 INTEGER NOT NULL DEFAULT 0,
  accumulated_bonus1 INTEGER NOT NULL DEFAULT 0,
  accumulated_bonus2 INTEGER NOT NULL DEFAULT 0,
  accumulated_bonus3 INTEGER NOT NULL DEFAULT 0,
  accumulated_bonus4 INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

INSERT OR IGNORE INTO seven_signs_status (id, current_cycle, festival_cycle, active_period, date, previous_winner, dawn_stone_score, dawn_festival_score, dusk_stone_score, dusk_festival_score, avarice_owner, gnosis_owner, strife_owner, avarice_dawn_score, gnosis_dawn_score, strife_dawn_score, avarice_dusk_score, gnosis_dusk_score, strife_dusk_score, accumulated_bonus0, accumulated_bonus1, accumulated_bonus2, accumulated_bonus3, accumulated_bonus4) VALUES
  (0, 1, 1, 'COMPETITION', 0, 'NORMAL', 0, 0, 0, 0, 'NORMAL', 'NORMAL', 'NORMAL', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- ========== seven_signs ==========
CREATE TABLE IF NOT EXISTS seven_signs (
  char_obj_id INTEGER NOT NULL DEFAULT 0,
  cabal TEXT NOT NULL DEFAULT 'NORMAL',
  seal TEXT NOT NULL DEFAULT 'NONE',
  red_stones INTEGER NOT NULL DEFAULT 0,
  green_stones INTEGER NOT NULL DEFAULT 0,
  blue_stones INTEGER NOT NULL DEFAULT 0,
  ancient_adena_amount REAL NOT NULL DEFAULT 0,
  contribution_score REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (char_obj_id)
);

-- ========== heroes ==========
CREATE TABLE IF NOT EXISTS heroes (
  char_id REAL NOT NULL DEFAULT 0,
  class_id REAL NOT NULL DEFAULT 0,
  count REAL NOT NULL DEFAULT 0,
  played REAL NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 0,
  message TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (char_id)
);

-- ========== heroes_diary ==========
CREATE TABLE IF NOT EXISTS heroes_diary (
  char_id INTEGER NOT NULL,
  time INTEGER NOT NULL DEFAULT 0,
  action INTEGER NOT NULL DEFAULT 0,
  param INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_heroes_diary_char_id ON heroes_diary(char_id);

-- ========== grandboss_list ==========
CREATE TABLE IF NOT EXISTS grandboss_list (
  player_id REAL NOT NULL,
  zone REAL NOT NULL,
  PRIMARY KEY (player_id, zone)
);

-- ========== bbs_forum ==========
-- Original UNIQUE KEY id -> separate UNIQUE INDEX (SQLite supports UNIQUE in CREATE TABLE but Flyway parser may not).
CREATE TABLE IF NOT EXISTS bbs_forum (
  id INTEGER NOT NULL DEFAULT 0,
  type TEXT NOT NULL DEFAULT '0',
  access TEXT NOT NULL DEFAULT '0',
  owner_id INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bbs_forum_id ON bbs_forum(id);

-- ========== bbs_topic ==========
CREATE TABLE IF NOT EXISTS bbs_topic (
  id INTEGER NOT NULL DEFAULT 0,
  forum_id INTEGER NOT NULL DEFAULT 0,
  name TEXT NOT NULL DEFAULT '',
  date REAL NOT NULL DEFAULT 0,
  owner_name TEXT NOT NULL DEFAULT '0',
  owner_id INTEGER NOT NULL DEFAULT 0
);

-- ========== bbs_post ==========
CREATE TABLE IF NOT EXISTS bbs_post (
  id INTEGER NOT NULL DEFAULT 0,
  owner_name TEXT NOT NULL DEFAULT '',
  owner_id INTEGER NOT NULL DEFAULT 0,
  date REAL NOT NULL DEFAULT 0,
  topic_id INTEGER NOT NULL DEFAULT 0,
  forum_id INTEGER NOT NULL DEFAULT 0,
  txt TEXT NOT NULL
);

-- ========== bbs_mail ==========
-- TIMESTAMP with ON UPDATE CURRENT_TIMESTAMP — replaced with TEXT defaults; updated_at handled in app code.
CREATE TABLE IF NOT EXISTS bbs_mail (
  id INTEGER NOT NULL DEFAULT 0,
  receiver_id INTEGER NOT NULL DEFAULT 0,
  sender_id INTEGER NOT NULL DEFAULT 0,
  location TEXT NOT NULL,
  recipients TEXT,
  subject TEXT,
  message TEXT,
  sent_date TEXT DEFAULT NULL,
  is_unread INTEGER DEFAULT 1,
  PRIMARY KEY (id)
);

-- ========== bbs_favorite ==========
CREATE TABLE IF NOT EXISTS bbs_favorite (
  id INTEGER NOT NULL DEFAULT 0,
  player_id INTEGER NOT NULL DEFAULT 0,
  title TEXT,
  bypass TEXT,
  date TEXT DEFAULT NULL,
  PRIMARY KEY (id)
);

-- ========== bbs_auction ==========
CREATE TABLE IF NOT EXISTS bbs_auction (
  id INTEGER NOT NULL DEFAULT 0,
  obj_Id INTEGER NOT NULL DEFAULT 0,
  item_id INTEGER NOT NULL DEFAULT 0,
  item_count INTEGER NOT NULL DEFAULT 0,
  item_enchant INTEGER NOT NULL DEFAULT 0,
  price_id INTEGER NOT NULL DEFAULT 0,
  price_count INTEGER NOT NULL DEFAULT 0,
  duration INTEGER,
  PRIMARY KEY (id)
);

-- ========== auctions ==========
CREATE TABLE IF NOT EXISTS auctions (
  clanhall_id INTEGER NOT NULL DEFAULT 0,
  bidder_name TEXT NOT NULL DEFAULT '',
  clan_oid INTEGER NOT NULL DEFAULT 0,
  clan_name TEXT NOT NULL DEFAULT '',
  max_bid INTEGER NOT NULL DEFAULT 0,
  time_bid INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clanhall_id, clan_oid)
);

-- ========== buylists ==========
CREATE TABLE IF NOT EXISTS buylists (
  buylist_id INTEGER,
  item_id INTEGER,
  count INTEGER NOT NULL DEFAULT 0,
  next_restock_time INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (buylist_id, item_id)
);

-- ========== bookmarks ==========
CREATE TABLE IF NOT EXISTS bookmarks (
  name TEXT NOT NULL DEFAULT '',
  obj_Id INTEGER NOT NULL DEFAULT 0,
  x INTEGER,
  y INTEGER,
  z INTEGER,
  PRIMARY KEY (name, obj_Id)
);

-- ========== buffer_schemes ==========
CREATE TABLE IF NOT EXISTS buffer_schemes (
  object_id INTEGER NOT NULL DEFAULT 0,
  scheme_name TEXT NOT NULL DEFAULT 'default',
  skills TEXT NOT NULL,
  levels TEXT NOT NULL,
  PRIMARY KEY (object_id, scheme_name)
);

-- ========== petition ==========
CREATE TABLE IF NOT EXISTS petition (
  oid INTEGER NOT NULL DEFAULT 0,
  type TEXT NOT NULL,
  petitioner_oid INTEGER NOT NULL DEFAULT 0,
  submit_date INTEGER NOT NULL DEFAULT 0,
  content TEXT NOT NULL,
  is_unread INTEGER NOT NULL DEFAULT 1,
  state TEXT NOT NULL,
  rate TEXT NOT NULL,
  feedback TEXT NOT NULL,
  responders TEXT NOT NULL,
  PRIMARY KEY (oid)
);

-- ========== petition_message ==========
CREATE TABLE IF NOT EXISTS petition_message (
  id INTEGER NOT NULL DEFAULT 0,
  petition_oid INTEGER NOT NULL DEFAULT 0,
  player_oid INTEGER NOT NULL DEFAULT 0,
  type TEXT NOT NULL,
  player_name TEXT NOT NULL,
  content TEXT NOT NULL,
  PRIMARY KEY (id, petition_oid)
);

-- ========== fishing_championship ==========
CREATE TABLE IF NOT EXISTS fishing_championship (
  player_name TEXT NOT NULL,
  fish_length REAL NOT NULL,
  rewarded INTEGER NOT NULL
);

-- ========== games ==========
CREATE TABLE IF NOT EXISTS games (
  id INTEGER NOT NULL DEFAULT 0,
  idnr INTEGER NOT NULL DEFAULT 0,
  number1 INTEGER NOT NULL DEFAULT 0,
  number2 INTEGER NOT NULL DEFAULT 0,
  prize INTEGER NOT NULL DEFAULT 0,
  newprize INTEGER NOT NULL DEFAULT 0,
  prize1 INTEGER NOT NULL DEFAULT 0,
  prize2 INTEGER NOT NULL DEFAULT 0,
  prize3 INTEGER NOT NULL DEFAULT 0,
  enddate REAL NOT NULL DEFAULT 0,
  finished INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id, idnr)
);

-- ========== mdt_bets ==========
CREATE TABLE IF NOT EXISTS mdt_bets (
  lane_id INTEGER DEFAULT 0,
  bet INTEGER DEFAULT 0,
  PRIMARY KEY (lane_id)
);

INSERT OR IGNORE INTO mdt_bets (lane_id, bet) VALUES
  (1, 0), (2, 0), (3, 0), (4, 0),
  (5, 0), (6, 0), (7, 0), (8, 0);

-- ========== mdt_history ==========
CREATE TABLE IF NOT EXISTS mdt_history (
  race_id INTEGER DEFAULT 0,
  first INTEGER DEFAULT 0,
  second INTEGER DEFAULT 0,
  odd_rate REAL DEFAULT 0,
  PRIMARY KEY (race_id)
);

-- ========== character_offline_trade_items ==========
CREATE TABLE IF NOT EXISTS character_offline_trade_items (
  charId INTEGER NOT NULL,
  item INTEGER NOT NULL DEFAULT 0,
  count INTEGER NOT NULL DEFAULT 0,
  price INTEGER NOT NULL DEFAULT 0,
  enchant INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_offline_trade_items_charId ON character_offline_trade_items(charId);
CREATE INDEX IF NOT EXISTS idx_offline_trade_items_item ON character_offline_trade_items(item);

-- ========== character_offline_trade ==========
CREATE TABLE IF NOT EXISTS character_offline_trade (
  charId INTEGER NOT NULL,
  time INTEGER NOT NULL DEFAULT 0,
  type INTEGER NOT NULL DEFAULT 0,
  title TEXT,
  PRIMARY KEY (charId)
);

-- ========== mods_wedding ==========
CREATE TABLE IF NOT EXISTS mods_wedding (
  id INTEGER NOT NULL DEFAULT 0,
  requesterId INTEGER NOT NULL DEFAULT 0,
  partnerId INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ========== hwid_bans ==========
CREATE TABLE IF NOT EXISTS hwid_bans (
  HWID TEXT,
  HWIDSecond TEXT,
  expiretime INTEGER NOT NULL DEFAULT 0,
  comments TEXT DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_hwid_bans_hwid ON hwid_bans(HWID);

-- ========== hwid_extra_boxes ==========
CREATE TABLE IF NOT EXISTS hwid_extra_boxes (
  hwid TEXT NOT NULL,
  extra_boxes INTEGER NOT NULL,
  PRIMARY KEY (hwid)
);

-- ========== hwid_info ==========
-- Original LockType ENUM('PLAYER_LOCK','ACCOUNT_LOCK','NONE') → TEXT + CHECK.
CREATE TABLE IF NOT EXISTS hwid_info (
  HWID TEXT NOT NULL DEFAULT '',
  Account TEXT NOT NULL DEFAULT '',
  PlayerID INTEGER NOT NULL DEFAULT 0,
  LockType TEXT NOT NULL DEFAULT 'NONE' CHECK (LockType IN ('PLAYER_LOCK', 'ACCOUNT_LOCK', 'NONE')),
  PRIMARY KEY (HWID)
);

-- ========== server_memo ==========
CREATE TABLE IF NOT EXISTS server_memo (
  var TEXT NOT NULL DEFAULT '',
  value TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (var)
);

-- ========== balance_classes ==========
CREATE TABLE IF NOT EXISTS balance_classes (
  class_id_attacker INTEGER NOT NULL,
  class_id_target INTEGER NOT NULL,
  p_atk_mod REAL DEFAULT 1.00,
  m_atk_mod REAL DEFAULT 1.00,
  p_def_mod REAL DEFAULT 1.00,
  m_def_mod REAL DEFAULT 1.00,
  PRIMARY KEY (class_id_attacker, class_id_target)
);

-- ========== balance_vulnerability ==========
CREATE TABLE IF NOT EXISTS balance_vulnerability (
  skill_type TEXT NOT NULL PRIMARY KEY,
  multiplier REAL NOT NULL DEFAULT 1.00
);

INSERT OR IGNORE INTO balance_vulnerability (skill_type, multiplier) VALUES
  ('BLEED', 1.00),
  ('POISON', 1.00),
  ('STUN', 1.00),
  ('PARALYZE', 1.00),
  ('ROOT', 1.00),
  ('SLEEP', 1.00),
  ('DERANGEMENT', 1.00),
  ('CONFUSION', 1.00),
  ('DEBUFF', 1.00),
  ('CANCEL', 1.00);

-- ========== events_custom_data ==========
CREATE TABLE IF NOT EXISTS events_custom_data (
  event_name TEXT NOT NULL,
  status REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (event_name)
);

-- ========== rainbowsprings_attacker_list ==========
CREATE TABLE IF NOT EXISTS rainbowsprings_attacker_list (
  clanId INTEGER NOT NULL DEFAULT 0,
  war_decrees_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (clanId)
);

-- ========== donations_payments ==========
CREATE TABLE IF NOT EXISTS donations_payments (
  purchase_id INTEGER NOT NULL DEFAULT 0,
  mp_preference_id TEXT,
  paypal_invoice_id TEXT,
  qrcode TEXT,
  link TEXT,
  PRIMARY KEY (purchase_id)
);

-- ========== donations ==========
-- Original UNIQUE KEY (purchase_id, payment_id) — duplicate of purchase_id PK; recreated as composite UNIQUE INDEX.
CREATE TABLE IF NOT EXISTS donations (
  purchase_id INTEGER NOT NULL DEFAULT 0,
  payment_id TEXT,
  payment_method TEXT,
  player_id INTEGER NOT NULL DEFAULT 0,
  email TEXT NOT NULL DEFAULT '',
  product_id INTEGER NOT NULL DEFAULT 0,
  quantity INTEGER NOT NULL DEFAULT 0,
  unit_price REAL NOT NULL DEFAULT 0,
  currency TEXT,
  date INTEGER DEFAULT 0,
  status TEXT NOT NULL DEFAULT '',
  terms INTEGER DEFAULT 0,
  PRIMARY KEY (purchase_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_donations_purchase_payment ON donations(purchase_id, payment_id);

-- ========== autofarm_time_usage ==========
-- Original DEFAULT current_timestamp() on datetime → SQLite uses CURRENT_TIMESTAMP literal which is supported.
CREATE TABLE IF NOT EXISTS autofarm_time_usage (
  player_id INTEGER NOT NULL,
  time_used INTEGER DEFAULT 0,
  last_reset TEXT DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id)
);

-- ========== autofarm_areas ==========
CREATE TABLE IF NOT EXISTS autofarm_areas (
  player_id INTEGER NOT NULL DEFAULT 0,
  area_id INTEGER NOT NULL DEFAULT 0,
  name TEXT,
  type TEXT,
  PRIMARY KEY (player_id, area_id)
);

-- ========== autofarm_skills ==========
CREATE TABLE IF NOT EXISTS autofarm_skills (
  player_id INTEGER NOT NULL,
  skill_id INTEGER NOT NULL,
  slot INTEGER NOT NULL,
  PRIMARY KEY (player_id, skill_id)
);

-- ========== autofarm_nodes ==========
CREATE TABLE IF NOT EXISTS autofarm_nodes (
  node_id INTEGER NOT NULL DEFAULT 0,
  area_id INTEGER NOT NULL DEFAULT 0,
  loc_x INTEGER NOT NULL DEFAULT 0,
  loc_y INTEGER NOT NULL DEFAULT 0,
  loc_z INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (area_id, node_id)
);

-- ========== autofarm_player_data ==========
CREATE TABLE IF NOT EXISTS autofarm_player_data (
  player_id INTEGER NOT NULL,
  time_used INTEGER DEFAULT 0,
  PRIMARY KEY (player_id)
);

-- ========== player_emails ==========
-- status ENUM → TEXT + CHECK; AUTO_INCREMENT → SQLite rowid-backed PK.
CREATE TABLE IF NOT EXISTS player_emails (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id INTEGER NOT NULL,
  target_id INTEGER NOT NULL,
  email_id INTEGER NOT NULL,
  item_object_id INTEGER NOT NULL,
  item_id INTEGER NOT NULL,
  count INTEGER NOT NULL,
  enchant_level INTEGER NOT NULL,
  is_augmented INTEGER NOT NULL DEFAULT 0,
  augment_id INTEGER,
  is_paid INTEGER NOT NULL DEFAULT 0,
  payment_item_id INTEGER,
  payment_item_count INTEGER,
  status TEXT DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CLAIMED', 'EXPIRED')),
  expiration_time INTEGER NOT NULL,
  created_time INTEGER NOT NULL
);

-- ========== player_droplist_config ==========
CREATE TABLE IF NOT EXISTS player_droplist_config (
  player_id INTEGER NOT NULL,
  item_id INTEGER NOT NULL,
  PRIMARY KEY (player_id, item_id)
);

-- ========== buffshop ==========
CREATE TABLE IF NOT EXISTS buffshop (
  ownerId INTEGER NOT NULL,
  buffs TEXT NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  x INTEGER,
  y INTEGER,
  z INTEGER,
  heading INTEGER,
  tempBuffShopPrice TEXT,
  store_message TEXT,
  value TEXT,
  class_id INTEGER NOT NULL DEFAULT 0,
  sex INTEGER NOT NULL DEFAULT 0,
  face INTEGER NOT NULL DEFAULT 0,
  hair_style INTEGER NOT NULL DEFAULT 0,
  hair_color INTEGER NOT NULL DEFAULT 0,
  equipped_items TEXT,
  PRIMARY KEY (ownerId)
);

-- ========== dungeon_cooldowns ==========
CREATE TABLE IF NOT EXISTS dungeon_cooldowns (
  dungeon_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  last_join INTEGER NOT NULL,
  next_join INTEGER NOT NULL,
  ip_address TEXT,
  stage INTEGER NOT NULL,
  PRIMARY KEY (dungeon_id, player_id)
);