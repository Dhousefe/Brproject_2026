package ext.mods.config;

/**
 * Phase 4: logical ownership of configuration domains.
 * Fields and load() live in the matching {@code Config*} classes under this package.
 */
public enum ConfigDomain
{
	RATES("rates.properties", "ConfigRates"),
	SERVER("server.properties + items.properties", "ConfigServer"),
	PLAYERS("players.properties", "ConfigPlayers"),
	PROTECTION("protection.properties", "ConfigProtection"),
	OFFLINE_SHOP("offlineshop.properties", "ConfigOfflineShop"),
	SAFE_DISCONNECT("safedisconnect.properties", "ConfigSafeDisconnect"),
	BOSS_ZERG("bosszerg.properties", "ConfigBossZerg"),
	DONATION("donation.properties + Pix.properties", "ConfigDonation"),
	EVENTS("events.properties", "ConfigEvents"),
	GEOENGINE("geoengine.properties", "ConfigGeoengine"),
	LANGUAGE("language.properties", "ConfigLanguage"),
	CLANS("clans.properties", "ConfigClans"),
	NPCS("npcs.properties", "ConfigNpcs"),
	SIEGE("siege.properties", "ConfigSiege"),
	TRANSLATOR("translator.properties", "ConfigTranslator"),
	PROJECT("project.properties + chatfilter.txt", "ConfigProject"),
	LOGIN("loginserver.properties", "ConfigLogin"),
	HEXID("hexid.txt", "ConfigHexId"),
	BOSS_HEAL("bossHeal.properties", "ConfigBossHeal"),
	BOSS_JEWELS("BossJewelUpgrades.properties", "ConfigBossJewels");

	private final String files;
	private final String owns;

	ConfigDomain(String files, String owns)
	{
		this.files = files;
		this.owns = owns;
	}

	public String files()
	{
		return files;
	}

	public String owns()
	{
		return owns;
	}
}
