-- =========================================================================
-- V1_0_6: Adiciona colunas para restrição de tempo e ciclo de 24h no AutoFarm
-- =========================================================================

ALTER TABLE `autofarm_player_data`
    ADD COLUMN IF NOT EXISTS `cycle_start_time` BIGINT(20) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `extra_time` BIGINT(20) DEFAULT 0;
