CREATE TABLE IF NOT EXISTS site_vote_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at BIGINT NOT NULL,
  delivery_id VARCHAR(128) NOT NULL,
  player_name VARCHAR(45) NOT NULL,
  account_name VARCHAR(45) NOT NULL DEFAULT '',
  character_id INT NOT NULL DEFAULT 0,
  ip VARCHAR(64) NOT NULL DEFAULT '',
  rewards_summary VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'DELIVERED',
  PRIMARY KEY (id),
  UNIQUE KEY uq_site_vote_delivery (delivery_id),
  KEY idx_site_vote_player_created (player_name, created_at),
  KEY idx_site_vote_account_created (account_name, created_at),
  KEY idx_site_vote_ip_created (ip, created_at)
);
