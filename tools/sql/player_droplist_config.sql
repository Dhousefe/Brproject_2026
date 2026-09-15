CREATE TABLE IF NOT EXISTS player_droplist_config (
    player_id INT NOT NULL,
    item_id INT NOT NULL,
    PRIMARY KEY (player_id, item_id)
);
