CREATE TABLE `buffshop` (
  `ownerId` int(11) NOT NULL,
  `buffs` text NOT NULL,
  `title` varchar(255) NOT NULL DEFAULT '',
  `x` int(7) DEFAULT NULL,
  `y` int(7) DEFAULT NULL,
  `z` int(7) DEFAULT NULL,
  `heading` int(7) DEFAULT NULL,
  `tempBuffShopPrice` varchar(32) DEFAULT NULL,
  `store_message` varchar(50) DEFAULT NULL,
  `value` varchar(64) DEFAULT NULL,
  `class_id` int(11) NOT NULL DEFAULT 0,
  `sex` tinyint(1) NOT NULL DEFAULT 0,
  `face` tinyint(1) NOT NULL DEFAULT 0,
  `hair_style` tinyint(1) NOT NULL DEFAULT 0,
  `hair_color` tinyint(1) NOT NULL DEFAULT 0,
  `equipped_items` text DEFAULT NULL,
  PRIMARY KEY (`ownerId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;