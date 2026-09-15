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
 * Phase 4 config domain: ConfigSafeDisconnect.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigSafeDisconnect
{
   private ConfigSafeDisconnect()
   {
   }

   public static boolean SAFEDISCONNECT_ENABLED;
   public static long SAFEDISCONNECT_TIMEOUT_MS;
   public static boolean SAFEDISCONNECT_ALLOW_OLYMPIAD;
   public static boolean SAFEDISCONNECT_ALLOW_BOSS;
   public static boolean SAFEDISCONNECT_ALLOW_QUEST;
   public static boolean SAFEDISCONNECT_ALLOW_FARM;
   public static boolean SAFEDISCONNECT_INTEGRATION_OLYMPIAD;
   public static boolean SAFEDISCONNECT_INTEGRATION_DUNGEON;
   public static boolean SAFEDISCONNECT_INTEGRATION_TOURNAMENT;
   public static boolean SAFEDISCONNECT_INTEGRATION_TVT;
   public static boolean SAFEDISCONNECT_INTEGRATION_CTF;
   public static boolean SAFEDISCONNECT_INTEGRATION_DM;
   public static boolean SAFEDISCONNECT_INTEGRATION_LM;
   public static boolean SAFEDISCONNECT_INVULNERABLE;
   public static boolean SAFEDISCONNECT_IMMOBILIZE;
   public static boolean SAFEDISCONNECT_CHANGE_NAME_COLOR;
   public static long SAFEDISCONNECT_AUTOFARM_RESTORE_DELAY_MS;
   public static String SAFEDISCONNECT_TITLE;
   public static int SAFEDISCONNECT_NAME_COLOR;

   public static void load() {
      ExProperties safeDisconnect = Config.initProperties(Config.SAFE_DISCONNECT_FILE);
      ConfigSafeDisconnect.SAFEDISCONNECT_ENABLED = safeDisconnect.getProperty("SafeDisconnectEnabled", true);      ConfigSafeDisconnect.SAFEDISCONNECT_TIMEOUT_MS = safeDisconnect.getProperty("SafeDisconnectTimeoutMs", 300000L);      ConfigSafeDisconnect.SAFEDISCONNECT_ALLOW_OLYMPIAD = safeDisconnect.getProperty("SafeDisconnectAllowOlympiad", false);      ConfigSafeDisconnect.SAFEDISCONNECT_ALLOW_BOSS = safeDisconnect.getProperty("SafeDisconnectAllowBoss", false);      ConfigSafeDisconnect.SAFEDISCONNECT_ALLOW_QUEST = safeDisconnect.getProperty("SafeDisconnectAllowQuest", false);      ConfigSafeDisconnect.SAFEDISCONNECT_ALLOW_FARM = safeDisconnect.getProperty("SafeDisconnectAllowFarm", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_OLYMPIAD = safeDisconnect.getProperty("SafeDisconnectIntegrationOlympiad", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_DUNGEON = safeDisconnect.getProperty("SafeDisconnectIntegrationDungeon", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_TOURNAMENT = safeDisconnect.getProperty("SafeDisconnectIntegrationTournament", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_TVT = safeDisconnect.getProperty("SafeDisconnectIntegrationTvT", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_CTF = safeDisconnect.getProperty("SafeDisconnectIntegrationCTF", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_DM = safeDisconnect.getProperty("SafeDisconnectIntegrationDM", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INTEGRATION_LM = safeDisconnect.getProperty("SafeDisconnectIntegrationLM", true);      ConfigSafeDisconnect.SAFEDISCONNECT_INVULNERABLE = safeDisconnect.getProperty("SafeDisconnectInvulnerable", true);      ConfigSafeDisconnect.SAFEDISCONNECT_IMMOBILIZE = safeDisconnect.getProperty("SafeDisconnectImmobilize", true);      ConfigSafeDisconnect.SAFEDISCONNECT_CHANGE_NAME_COLOR = safeDisconnect.getProperty("SafeDisconnectChangeNameColor", false);      ConfigSafeDisconnect.SAFEDISCONNECT_AUTOFARM_RESTORE_DELAY_MS = safeDisconnect.getProperty("SafeDisconnectAutoFarmRestoreDelayMs", 1500L);      ConfigSafeDisconnect.SAFEDISCONNECT_TITLE = safeDisconnect.getProperty("SafeDisconnectTitle", "Disconnect...");      String nameColor = safeDisconnect.getProperty("SafeDisconnectNameColor", "00FFFF");
      if (nameColor.startsWith("0x") || nameColor.startsWith("0X")) {
         nameColor = nameColor.substring(2);
      }

      try {
         ConfigSafeDisconnect.SAFEDISCONNECT_NAME_COLOR = Integer.decode("0x" + nameColor);      } catch (NumberFormatException var3) {
         ConfigSafeDisconnect.SAFEDISCONNECT_NAME_COLOR = Integer.decode("0x00FFFF");      }
   }
}
