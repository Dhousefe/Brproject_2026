CREATE TABLE balance_classes (
	class_id_attacker INT NOT NULL,
	class_id_target INT NOT NULL,
	p_atk_mod DECIMAL(5,2) DEFAULT 1.00,
	m_atk_mod DECIMAL(5,2) DEFAULT 1.00,
	p_def_mod DECIMAL(5,2) DEFAULT 1.00,
	m_def_mod DECIMAL(5,2) DEFAULT 1.00,
	PRIMARY KEY (class_id_attacker, class_id_target)
);
