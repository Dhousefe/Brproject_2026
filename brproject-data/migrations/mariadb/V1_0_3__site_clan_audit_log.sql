CREATE TABLE IF NOT EXISTS site_clan_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at BIGINT NOT NULL,
  login VARCHAR(45) NOT NULL,
  character_id INT NOT NULL DEFAULT 0,
  clan_id INT NOT NULL DEFAULT 0,
  action VARCHAR(64) NOT NULL,
  old_value VARCHAR(255) DEFAULT NULL,
  new_value VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_site_clan_audit_login_created (login, created_at),
  KEY idx_site_clan_audit_clan_created (clan_id, created_at),
  KEY idx_site_clan_audit_action_created (action, created_at)
);
