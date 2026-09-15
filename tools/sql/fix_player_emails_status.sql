-- Script para corrigir a coluna status da tabela player_emails
-- Este script verifica e corrige a estrutura da tabela se necessário

-- Verificar a estrutura atual da tabela
DESCRIBE player_emails;

-- Verificar se a coluna status existe e seu tipo
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'player_emails' 
AND COLUMN_NAME = 'status';

-- Se a coluna status não existir ou estiver com tipo incorreto, recriar
-- Primeiro, fazer backup dos dados existentes
CREATE TABLE player_emails_backup AS SELECT * FROM player_emails;

-- Recriar a tabela com a estrutura correta
DROP TABLE IF EXISTS player_emails;

CREATE TABLE `player_emails` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sender_id` int(11) NOT NULL,
  `target_id` int(11) NOT NULL,
  `email_id` int(11) NOT NULL,
  `item_object_id` int(11) NOT NULL,
  `item_id` smallint(6) NOT NULL,
  `count` int(11) NOT NULL,
  `enchant_level` smallint(6) NOT NULL,
  `is_augmented` tinyint(1) NOT NULL DEFAULT 0,
  `augment_id` int(11) DEFAULT NULL,
  `is_paid` tinyint(1) NOT NULL DEFAULT 0,
  `payment_item_id` smallint(6) DEFAULT NULL,
  `payment_item_count` int(11) DEFAULT NULL,
  `status` enum('PENDING','ACCEPTED','REJECTED','CLAIMED','EXPIRED') DEFAULT 'PENDING',
  `expiration_time` bigint(20) NOT NULL,
  `created_time` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_email_id` (`email_id`),
  KEY `idx_target_id` (`target_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Restaurar os dados do backup (apenas os que têm status válido)
INSERT INTO player_emails 
SELECT * FROM player_emails_backup 
WHERE status IN ('PENDING','ACCEPTED','REJECTED','CLAIMED','EXPIRED');

-- Verificar se os dados foram restaurados corretamente
SELECT COUNT(*) as total_records FROM player_emails;
SELECT status, COUNT(*) as count FROM player_emails GROUP BY status;

-- Remover a tabela de backup
DROP TABLE player_emails_backup;

-- Verificar a estrutura final
DESCRIBE player_emails;
