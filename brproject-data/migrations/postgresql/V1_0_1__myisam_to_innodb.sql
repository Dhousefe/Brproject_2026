-- BrProject P3 hygiene: convert remaining MyISAM tables to InnoDB.
-- Safe on DBs already on InnoDB (ALTER is a no-op-ish re-engine to InnoDB).
-- Fresh installs after tools/sql fix already create these as InnoDB.

ALTER TABLE "items_delayed" ;
ALTER TABLE "character_offline_trade" ;
ALTER TABLE "character_offline_trade_items" ;
ALTER TABLE "buffshop" ;
