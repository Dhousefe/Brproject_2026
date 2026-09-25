INSERT INTO gameservers (server_id, hexid, host)
VALUES (1, '${GAME_SERVER_HEXID}', '${GAME_SERVER_HOST}')
ON CONFLICT (server_id) DO UPDATE SET
  hexid = EXCLUDED.hexid,
  host = EXCLUDED.host;
