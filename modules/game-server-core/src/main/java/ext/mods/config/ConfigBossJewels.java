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
 * Phase 4 config domain: ConfigBossJewels.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigBossJewels
{
   private ConfigBossJewels()
   {
   }

   public static boolean UPGRADE_BOSS_JEWELS_ENCHANT;
   public static List<ext.mods.Config.JewelUpgrade> UPGRADE_BOSS_JEWELS = new ArrayList<>();
   public static Set<Integer> UPGRADEABLE_BOSS_JEWELS = new HashSet<>();

   public static void load() {
      ExProperties bossJewelUpgrades = Config.initProperties(Config.BOSS_JEWEL_UPGRADES_FILE);
      ConfigBossJewels.UPGRADE_BOSS_JEWELS_ENCHANT = bossJewelUpgrades.getProperty("UpgradeBossJewelsEnchant", false);
      Config.LOGGER.info("UpgradeBossJewelsEnchant: {}", new Object[]{ConfigBossJewels.UPGRADE_BOSS_JEWELS_ENCHANT});
      String[] jewelUpgrades = bossJewelUpgrades.getProperty("UpgradeBossJewels", "").split(",");

      for (String upgrade : jewelUpgrades) {
         if (!upgrade.trim().isEmpty()) {
            String[] parts = upgrade.split(":");
            if (parts.length == 3) {
               try {
                  int itemId = Integer.parseInt(parts[0].trim());
                  int enchantLevel = Integer.parseInt(parts[1].trim());
                  int newItemId = Integer.parseInt(parts[2].trim());
                  ConfigBossJewels.UPGRADE_BOSS_JEWELS.add(new ext.mods.Config.JewelUpgrade(itemId, enchantLevel, newItemId));
                  ConfigBossJewels.UPGRADEABLE_BOSS_JEWELS.add(itemId);
                  Config.LOGGER.info("Loaded JewelUpgrade: itemId={}, enchantLevel={}, newItemId={}", new Object[]{itemId, enchantLevel, newItemId});
               } catch (NumberFormatException var10) {
                  Config.LOGGER.error("Failed to parse JewelUpgrade: {}", new Object[]{upgrade, var10});
               }
            } else {
               Config.LOGGER.warn("Invalid JewelUpgrade format: {}", new Object[]{upgrade});
            }
         }
      }

      Config.LOGGER.info("Loaded {} boss jewel upgrades.", new Object[]{ConfigBossJewels.UPGRADE_BOSS_JEWELS.size()});
      Config.LOGGER.info("Loaded {} upgradeable boss jewels.", new Object[]{ConfigBossJewels.UPGRADEABLE_BOSS_JEWELS.size()});
   }


}
