CREATE TABLE IF NOT EXISTS site_clan_audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at INTEGER NOT NULL,
  login TEXT NOT NULL,
  character_id INTEGER NOT NULL DEFAULT 0,
  clan_id INTEGER NOT NULL DEFAULT 0,
  action TEXT NOT NULL,
  old_value TEXT,
  new_value TEXT
);

CREATE INDEX IF NOT EXISTS idx_site_clan_audit_login_created ON site_clan_audit_log (login, created_at);
CREATE INDEX IF NOT EXISTS idx_site_clan_audit_clan_created ON site_clan_audit_log (clan_id, created_at);
CREATE INDEX IF NOT EXISTS idx_site_clan_audit_action_created ON site_clan_audit_log (action, created_at);
