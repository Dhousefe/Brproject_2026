CREATE TABLE IF NOT EXISTS site_vote_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at INTEGER NOT NULL,
  delivery_id TEXT NOT NULL UNIQUE,
  player_name TEXT NOT NULL,
  account_name TEXT NOT NULL DEFAULT '',
  character_id INTEGER NOT NULL DEFAULT 0,
  ip TEXT NOT NULL DEFAULT '',
  rewards_summary TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'DELIVERED'
);
CREATE INDEX IF NOT EXISTS idx_site_vote_player_created ON site_vote_logs (player_name, created_at);
CREATE INDEX IF NOT EXISTS idx_site_vote_account_created ON site_vote_logs (account_name, created_at);
CREATE INDEX IF NOT EXISTS idx_site_vote_ip_created ON site_vote_logs (ip, created_at);
