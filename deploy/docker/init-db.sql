-- Phase 5: ensure database exists for non-root app user (MariaDB entrypoint also creates MARIADB_DATABASE).
-- Kept idempotent for operators re-running init.
CREATE DATABASE IF NOT EXISTS l2jdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
