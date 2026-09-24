INSERT INTO gameservers (server_id, hexid, host)
VALUES (1, '${GAME_SERVER_HEXID}', '${GAME_SERVER_HOST}')
ON DUPLICATE KEY UPDATE
  hexid = VALUES(hexid),
  host = VALUES(host);
