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
 * Phase 4 config domain: ConfigOfflineShop.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigOfflineShop
{
   private ConfigOfflineShop()
   {
   }

   public static boolean OFFLINE_TRADE_ENABLE;
   public static boolean OFFLINE_CRAFT_ENABLE;
   public static boolean OFFLINE_MODE_IN_PEACE_ZONE;
   public static boolean OFFLINE_MODE_NO_DAMAGE;
   public static boolean RESTORE_OFFLINERS;
   public static int OFFLINE_MAX_DAYS;
   public static boolean OFFLINE_DISCONNECT_FINISHED;
   public static boolean OFFLINE_SLEEP_EFFECT;
   public static boolean RESTORE_STORE_ITEMS;

   public static void load() {
      ExProperties offline = Config.initProperties(Config.OFFLINE_FILE);
      ConfigOfflineShop.OFFLINE_TRADE_ENABLE = offline.getProperty("OfflineTradeEnable", false);      ConfigOfflineShop.OFFLINE_CRAFT_ENABLE = offline.getProperty("OfflineCraftEnable", false);      ConfigOfflineShop.OFFLINE_MODE_IN_PEACE_ZONE = offline.getProperty("OfflineModeInPeaceZone", false);      ConfigOfflineShop.OFFLINE_MODE_NO_DAMAGE = offline.getProperty("OfflineModeNoDamage", false);      ConfigOfflineShop.RESTORE_OFFLINERS = offline.getProperty("RestoreOffliners", false);      ConfigOfflineShop.OFFLINE_MAX_DAYS = offline.getProperty("OfflineMaxDays", 10);      ConfigOfflineShop.OFFLINE_DISCONNECT_FINISHED = offline.getProperty("OfflineDisconnectFinished", true);      ConfigOfflineShop.OFFLINE_SLEEP_EFFECT = offline.getProperty("OfflineSleepEffect", true);      ConfigOfflineShop.RESTORE_STORE_ITEMS = offline.getProperty("RestoreStoreItems", false);   }
}
