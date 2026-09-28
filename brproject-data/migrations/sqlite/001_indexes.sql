-- Hot-path indexes for SQLite generated from data/Backup.sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_characters_char_name ON characters(char_name);
CREATE INDEX IF NOT EXISTS idx_characters_account_name ON characters(account_name);
CREATE INDEX IF NOT EXISTS idx_characters_online_account ON characters(online, account_name);
CREATE INDEX IF NOT EXISTS idx_items_owner_loc ON items(owner_id, loc);
CREATE INDEX IF NOT EXISTS idx_items_item_owner ON items(item_id, owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_gameservers_hexid ON gameservers(hexid);
CREATE INDEX IF NOT EXISTS idx_character_skills_save_char_class_time ON character_skills_save(char_obj_id, class_index, systime);
CREATE INDEX IF NOT EXISTS idx_accounts_last_active ON accounts(last_active);
CREATE INDEX IF NOT EXISTS idx_bbs_post_topic ON bbs_post(topic_id);
CREATE INDEX IF NOT EXISTS idx_bbs_topic_forum ON bbs_topic(forum_id);
