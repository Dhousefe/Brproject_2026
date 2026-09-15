-- Migração para bases que já tinham a tabela `donations` criada com esquema antigo.
-- CREATE TABLE IF NOT EXISTS do donations.sql NÃO adiciona colunas a uma tabela existente.
-- Execute este script UMA vez no MySQL/MariaDB da conta do gameserver.
--
-- Se alguma linha der erro "Duplicate column name", ignore (coluna já existe).

ALTER TABLE `donations` ADD COLUMN `payment_id` VARCHAR(30) DEFAULT NULL AFTER `purchase_id`;
ALTER TABLE `donations` ADD COLUMN `payment_method` VARCHAR(7) DEFAULT NULL AFTER `payment_id`;
ALTER TABLE `donations` ADD COLUMN `unit_price` DOUBLE NOT NULL DEFAULT 0 AFTER `quantity`;
ALTER TABLE `donations` ADD COLUMN `currency` VARCHAR(3) DEFAULT NULL AFTER `unit_price`;

-- Índice do esquema atual (opcional; falha se já existir com outro nome — ajuste se precisar)
-- ALTER TABLE `donations` ADD UNIQUE KEY `purchase_id_2` (`purchase_id`, `payment_id`);
