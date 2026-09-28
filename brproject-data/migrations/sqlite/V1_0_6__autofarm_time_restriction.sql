-- =========================================================================
-- V1_0_6: Adiciona colunas para restrição de tempo e ciclo de 24h no AutoFarm (SQLite)
-- =========================================================================

ALTER TABLE autofarm_player_data ADD COLUMN cycle_start_time INTEGER DEFAULT 0;
ALTER TABLE autofarm_player_data ADD COLUMN extra_time INTEGER DEFAULT 0;
