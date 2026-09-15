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
 * Phase 4 config domain: ConfigBossHeal.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigBossHeal
{
   private ConfigBossHeal()
   {
   }

   public static boolean BLOCK_HEAL_ON_RAIDBOSS;
   public static boolean BLOCK_HEAL_ON_GRANDBOSS;
   public static Set<Integer> BLOCKED_HEAL_SKILL_IDS = new HashSet<>();
   public static String HEAL_BLOCK_MESSAGE;

   public static void load() {
      ExProperties bossHeal = Config.initProperties(Config.BOSS_HEAL_FILE);
      ConfigBossHeal.BLOCK_HEAL_ON_RAIDBOSS = bossHeal.getProperty("BlockHealOnRaidBoss", true);      ConfigBossHeal.BLOCK_HEAL_ON_GRANDBOSS = bossHeal.getProperty("BlockHealOnGrandBoss", true);      String blockedIds = bossHeal.getProperty("BlockedHealSkillIds", "");
      if (!blockedIds.isEmpty()) {
         for (String id : blockedIds.split(",")) {
            try {
               ConfigBossHeal.BLOCKED_HEAL_SKILL_IDS.add(Integer.parseInt(id.trim()));            } catch (NumberFormatException var7) {
               Config.LOGGER.warn("Invalid skill ID in BlockedHealSkillIds: {}", new Object[]{id});
            }
         }
      }

      ConfigBossHeal.HEAL_BLOCK_MESSAGE = bossHeal.getProperty("HealBlockMessage", "");   }
}
