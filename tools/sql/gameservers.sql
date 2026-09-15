CREATE TABLE IF NOT EXISTS `gameservers` (
  `server_id` int(11) NOT NULL default '0',
  `hexid` varchar(50) NOT NULL default '',
  `host` varchar(50) NOT NULL default '',
  PRIMARY KEY (`server_id`)
);

REPLACE INTO `gameservers` (`server_id`, `hexid`, `host`) VALUES
	(1, '-48a6ee8326718cf319b437606c521a57', '127.0.0.1\r\n');