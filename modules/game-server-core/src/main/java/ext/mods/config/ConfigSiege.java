package ext.mods.config;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import ext.mods.Config;
import ext.mods.commons.config.ExProperties;
import ext.mods.gameserver.data.manager.CountryLocaleManager;
import ext.mods.gameserver.enums.GeoType;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.olympiad.enums.OlympiadPeriod;
import ext.mods.protection.hwid.crypt.FirstKey;

/**
 * Phase 4 config domain: ConfigSiege.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigSiege
{
   private ConfigSiege()
   {
   }

   public static int SIEGE_LENGTH;
   public static int MINIMUM_CLAN_LEVEL;
   public static int MAX_ATTACKERS_NUMBER;
   public static int MAX_DEFENDERS_NUMBER;
   public static int CH_MINIMUM_CLAN_LEVEL;
   public static int CH_MAX_ATTACKERS_NUMBER;
   public static boolean SIEGE_INFO;

   public static void load() {
      ExProperties sieges = Config.initProperties(Config.SIEGE_FILE);
      ConfigSiege.SIEGE_LENGTH = sieges.getProperty("SiegeLength", 120);      ConfigSiege.MINIMUM_CLAN_LEVEL = sieges.getProperty("SiegeClanMinLevel", 4);      ConfigSiege.MAX_ATTACKERS_NUMBER = sieges.getProperty("AttackerMaxClans", 10);      ConfigSiege.MAX_DEFENDERS_NUMBER = sieges.getProperty("DefenderMaxClans", 10);      ConfigSiege.CH_MINIMUM_CLAN_LEVEL = sieges.getProperty("ChSiegeClanMinLevel", 4);      ConfigSiege.CH_MAX_ATTACKERS_NUMBER = sieges.getProperty("ChAttackerMaxClans", 10);      ConfigSiege.SIEGE_INFO = sieges.getProperty("SiegeInfo", false);   }
}
