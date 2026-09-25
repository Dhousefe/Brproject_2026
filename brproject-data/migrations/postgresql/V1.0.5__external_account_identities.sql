CREATE TABLE IF NOT EXISTS external_account_identities (
  provider VARCHAR(16) NOT NULL,
  provider_user_id VARCHAR(20) NOT NULL,
  account_login VARCHAR(45) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (provider, provider_user_id),
  CONSTRAINT external_account_identities_account_login_key UNIQUE (account_login),
  CONSTRAINT external_account_identities_account_login_fkey FOREIGN KEY (account_login) REFERENCES accounts (login) ON DELETE CASCADE
);

