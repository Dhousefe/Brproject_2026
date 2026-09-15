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
 * Phase 4 config domain: ConfigBossZerg.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigBossZerg
{
   private ConfigBossZerg()
   {
   }

   public static boolean BOSS_ZERG_ENABLED;
   public static boolean BOSS_ZERG_FLAG_ENABLED;
   public static int BOSS_ZERG_FLAG_RANGE;
   public static long BOSS_ZERG_FLAG_RENEW_MS;
   public static int BOSS_ZERG_FLAG_MIN_PLAYERS;
   public static int BOSS_ZERG_FLAG_WARNING_RANGE;
   public static int BOSS_ZERG_FLAG_AREA_RADIUS;
   public static int BOSS_ZERG_FLAG_AREA_COLOR;
   public static int BOSS_ZERG_RANGE;
   public static int BOSS_ZERG_MIN_PARTY_SIZE;
   public static int BOSS_ZERG_MAX_ALLY_MEMBERS;
   public static boolean BOSS_ZERG_SHOW_ALLY_COUNT;
   public static boolean BOSS_ZERG_ANNOUNCE;
   public static long BOSS_ZERG_ANNOUNCE_COOLDOWN_MS;
   public static boolean BOSS_ZERG_SHOW_AREA;
   public static int BOSS_ZERG_AREA_RADIUS;
   public static long BOSS_ZERG_CHECK_INTERVAL_MS;
   public static boolean BOSS_ZERG_FLAG_AREA_ENABLED;
   public static boolean BOSS_ZERG_HEAL_PENALTY_ENABLED;
   public static double BOSS_ZERG_HEAL_PENALTY_MULTIPLIER;
   public static double BOSS_ZERG_PRAYER_REVERSE_MULTIPLIER;
   public static int[] BOSS_ZERG_PRAYER_SKILL_IDS = new int[0];
   public static Set<Integer> BOSS_ZERG_IGNORED_BOSS_IDS = new HashSet<>();

   public static void load() {
      ExProperties bossZerg = Config.initProperties(Config.BOSS_ZERG_FILE);
      ConfigBossZerg.BOSS_ZERG_ENABLED = bossZerg.getProperty("BossZergEnabled", true);      ConfigBossZerg.BOSS_ZERG_FLAG_ENABLED = bossZerg.getProperty("BossZergFlagEnabled", true);      ConfigBossZerg.BOSS_ZERG_FLAG_RANGE = bossZerg.getProperty("BossZergFlagRange", 1200);      ConfigBossZerg.BOSS_ZERG_FLAG_RENEW_MS = bossZerg.getProperty("BossZergFlagRenewMs", 10000L);      ConfigBossZerg.BOSS_ZERG_FLAG_MIN_PLAYERS = bossZerg.getProperty("BossZergFlagMinPlayers", 1);      ConfigBossZerg.BOSS_ZERG_FLAG_WARNING_RANGE = bossZerg.getProperty("BossZergFlagWarningRange", 1200);      ConfigBossZerg.BOSS_ZERG_FLAG_AREA_RADIUS = bossZerg.getProperty("BossZergFlagAreaRadius", 600);      String flagColor = bossZerg.getProperty("BossZergFlagAreaColor", "FFA500");
      if (flagColor.startsWith("0x") || flagColor.startsWith("0X")) {
         flagColor = flagColor.substring(2);
      }

      try {
         ConfigBossZerg.BOSS_ZERG_FLAG_AREA_COLOR = Integer.decode("0x" + flagColor);      } catch (NumberFormatException var14) {
         ConfigBossZerg.BOSS_ZERG_FLAG_AREA_COLOR = Integer.decode("0xFFA500");      }

      ConfigBossZerg.BOSS_ZERG_RANGE = bossZerg.getProperty("BossZergRange", 1200);      ConfigBossZerg.BOSS_ZERG_MIN_PARTY_SIZE = bossZerg.getProperty("BossZergMinPartySize", 3);      ConfigBossZerg.BOSS_ZERG_MAX_ALLY_MEMBERS = bossZerg.getProperty("BossZergMaxAllyMembers", 18);      ConfigBossZerg.BOSS_ZERG_SHOW_ALLY_COUNT = bossZerg.getProperty("BossZergShowAllyCount", true);      ConfigBossZerg.BOSS_ZERG_ANNOUNCE = bossZerg.getProperty("BossZergAnnounce", true);      ConfigBossZerg.BOSS_ZERG_ANNOUNCE_COOLDOWN_MS = bossZerg.getProperty("BossZergAnnounceCooldownMs", 60000L);      ConfigBossZerg.BOSS_ZERG_SHOW_AREA = bossZerg.getProperty("BossZergShowArea", true);      ConfigBossZerg.BOSS_ZERG_AREA_RADIUS = bossZerg.getProperty("BossZergAreaRadius", 1200);      ConfigBossZerg.BOSS_ZERG_CHECK_INTERVAL_MS = bossZerg.getProperty("BossZergCheckIntervalMs", 3000L);      ConfigBossZerg.BOSS_ZERG_FLAG_AREA_ENABLED = bossZerg.getProperty("BossZergFlagAreaEnabled", true);      ConfigBossZerg.BOSS_ZERG_HEAL_PENALTY_ENABLED = bossZerg.getProperty("BossZergHealPenaltyEnabled", true);      ConfigBossZerg.BOSS_ZERG_HEAL_PENALTY_MULTIPLIER = bossZerg.getProperty("BossZergHealPenaltyMultiplier", 0.75);      ConfigBossZerg.BOSS_ZERG_PRAYER_REVERSE_MULTIPLIER = bossZerg.getProperty("BossZergPrayerReverseMultiplier", 0.85);      String prayerIds = bossZerg.getProperty("BossZergPrayerSkillIds", "");
      if (prayerIds != null && !prayerIds.trim().isEmpty()) {
         String[] parts = prayerIds.split(",");
         List<Integer> ids = new ArrayList<>(parts.length);

         for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
               try {
                  ids.add(Integer.parseInt(trimmed));
               } catch (NumberFormatException var13) {
                  Config.LOGGER.warn("BossZerg: Invalid prayer skill id '{}'.", new Object[]{trimmed});
               }
            }
         }

         ConfigBossZerg.BOSS_ZERG_PRAYER_SKILL_IDS = ids.stream().mapToInt(Integer::intValue).toArray();      } else {
         ConfigBossZerg.BOSS_ZERG_PRAYER_SKILL_IDS = new int[0];      }

      String ignoredBossIds = bossZerg.getProperty("BossZergIgnoredBossIds", "");
      Set<Integer> ignoredIds = new HashSet<>();
      if (ignoredBossIds != null && !ignoredBossIds.trim().isEmpty()) {
         String[] parts = ignoredBossIds.split(",");

         for (String partx : parts) {
            String trimmed = partx.trim();
            if (!trimmed.isEmpty()) {
               try {
                  ignoredIds.add(Integer.parseInt(trimmed));
               } catch (NumberFormatException var12) {
                  Config.LOGGER.warn("BossZerg: Invalid ignored boss id '{}'.", new Object[]{trimmed});
               }
            }
         }
      }

      ConfigBossZerg.BOSS_ZERG_IGNORED_BOSS_IDS = ignoredIds;   }
}
