-- =========================================================================
-- V1_0_8: Adiciona protecao de hardware (HWID Guard) e compras do Site/Web (SQLite)
-- =========================================================================

CREATE TABLE IF NOT EXISTS accounts_hardware_guard (
	login TEXT NOT NULL,
	credential_id TEXT NOT NULL,
	public_key_der TEXT NOT NULL,
	algorithm INTEGER NOT NULL DEFAULT -7,
	device_name TEXT NOT NULL DEFAULT 'Windows Hello (TPM 2.0)',
	sign_count INTEGER NOT NULL DEFAULT 0,
	created_at INTEGER NOT NULL DEFAULT 0,
	last_used_at INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (login, credential_id)
);

CREATE INDEX IF NOT EXISTS idx_hw_guard_login ON accounts_hardware_guard (login);

CREATE TABLE IF NOT EXISTS site_shop_purchases (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	created_at INTEGER NOT NULL,
	account_name TEXT NOT NULL,
	character_id INTEGER NOT NULL DEFAULT 0,
	character_name TEXT NOT NULL,
	item_key TEXT NOT NULL,
	item_id INTEGER NOT NULL,
	item_name TEXT NOT NULL,
	item_count INTEGER NOT NULL,
	coin_price INTEGER NOT NULL,
	quantity INTEGER NOT NULL,
	total_coins INTEGER NOT NULL,
	delivery_mode TEXT NOT NULL DEFAULT 'ONLINE',
	status TEXT NOT NULL DEFAULT 'COMPLETED'
);

CREATE INDEX IF NOT EXISTS idx_shop_acc ON site_shop_purchases (account_name);
CREATE INDEX IF NOT EXISTS idx_shop_char ON site_shop_purchases (character_id);
