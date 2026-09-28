-- =========================================================================
-- V1_0_7: Criação das tabelas do Fail2Ban para SQLite
-- =========================================================================

CREATE TABLE IF NOT EXISTS fail2ban_bans (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	ip TEXT NOT NULL,
	jail TEXT NOT NULL,
	reason TEXT,
	ban_time INTEGER NOT NULL,
	expire_time INTEGER NOT NULL,
	status TEXT NOT NULL DEFAULT 'ACTIVE',
	source TEXT DEFAULT 'AUTO'
);

CREATE INDEX IF NOT EXISTS idx_f2b_ip_status ON fail2ban_bans(ip, status);
CREATE INDEX IF NOT EXISTS idx_f2b_expire ON fail2ban_bans(expire_time);

CREATE TABLE IF NOT EXISTS fail2ban_events (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	ts INTEGER NOT NULL,
	ip TEXT NOT NULL,
	jail TEXT NOT NULL,
	type TEXT NOT NULL,
	detail TEXT
);

CREATE INDEX IF NOT EXISTS idx_f2e_ts ON fail2ban_events(ts);
CREATE INDEX IF NOT EXISTS idx_f2e_ip ON fail2ban_events(ip);
