CREATE TABLE dungeon_cooldowns (
    dungeon_id INT NOT NULL,
    player_id INT NOT NULL,
    last_join BIGINT NOT NULL,
    next_join BIGINT NOT NULL,
    ip_address VARCHAR(45),
	stage INT NOT NULL,
    PRIMARY KEY (dungeon_id, player_id)
);
