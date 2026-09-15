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
 * Phase 4 config domain: ConfigRates.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigRates
{
   private ConfigRates()
   {
   }

   public static double RATE_XP;
   public static double RATE_SP;
   public static double RATE_PARTY_XP;
   public static double RATE_PARTY_SP;
   public static double RATE_DROP_CURRENCY;
   public static double RATE_DROP_SEAL_STONE;
   public static double RATE_DROP_ITEMS;
   public static double RATE_DROP_ITEMS_BY_RAID;
   public static double RATE_DROP_ITEMS_BY_GRAND;
   public static double RATE_DROP_SPOIL;
   public static double PREMIUM_RATE_XP;
   public static double PREMIUM_RATE_SP;
   public static double PREMIUM_RATE_DROP_CURRENCY;
   public static double PREMIUM_RATE_DROP_SEAL_STONE;
   public static double PREMIUM_RATE_DROP_SPOIL;
   public static double PREMIUM_RATE_DROP_ITEMS;
   public static double PREMIUM_RATE_DROP_ITEMS_BY_RAID;
   public static double PREMIUM_RATE_DROP_ITEMS_BY_GRAND;
   public static double PREMIUM_RATE_QUEST_DROP;
   public static double PREMIUM_RATE_QUEST_REWARD;
   public static double PREMIUM_RATE_QUEST_REWARD_XP;
   public static double PREMIUM_RATE_QUEST_REWARD_SP;
   public static double PREMIUM_RATE_QUEST_REWARD_ADENA;
   public static boolean DYNAMIC_XP;
   public static it.unimi.dsi.fastutil.ints.Int2DoubleMap DYNAMIC_XP_RATES = new it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap();
   public static double RATE_DROP_HERBS;
   public static int RATE_DROP_MANOR;
   public static double RATE_QUEST_DROP;
   public static double RATE_QUEST_REWARD;
   public static double RATE_QUEST_REWARD_XP;
   public static double RATE_QUEST_REWARD_SP;
   public static double RATE_QUEST_REWARD_ADENA;
   public static double RATE_KARMA_EXP_LOST;
   public static double RATE_SIEGE_GUARDS_PRICE;
   public static int PLAYER_DROP_LIMIT;
   public static int PLAYER_RATE_DROP;
   public static int PLAYER_RATE_DROP_ITEM;
   public static int PLAYER_RATE_DROP_EQUIP;
   public static int PLAYER_RATE_DROP_EQUIP_WEAPON;
   public static double PET_XP_RATE;
   public static int PET_FOOD_RATE;
   public static double SINEATER_XP_RATE;
   public static int KARMA_DROP_LIMIT;
   public static int KARMA_RATE_DROP;
   public static int KARMA_RATE_DROP_ITEM;
   public static int KARMA_RATE_DROP_EQUIP;
   public static int KARMA_RATE_DROP_EQUIP_WEAPON;
   public static double GRANDBOSS_RATE_XP;
   public static double GRANDBOSS_RATE_SP;
   public static double RAIDBOSS_RATE_XP;
   public static double RAIDBOSS_RATE_SP;

   public static void load() {
      ExProperties rates = Config.initProperties(Config.RATES_FILE);
      ConfigRates.RATE_XP = rates.getProperty("RateXp", 1.0);      ConfigRates.RATE_SP = rates.getProperty("RateSp", 1.0);      ConfigRates.RATE_PARTY_XP = rates.getProperty("RatePartyXp", 1.0);      ConfigRates.RATE_PARTY_SP = rates.getProperty("RatePartySp", 1.0);      ConfigRates.RATE_DROP_CURRENCY = rates.getProperty("RateDropCurrency", 1.0);      ConfigRates.RATE_DROP_SEAL_STONE = rates.getProperty("RateDropSealStone", 1.0);      ConfigRates.RATE_DROP_ITEMS = rates.getProperty("RateDropItems", 1.0);      ConfigRates.RATE_DROP_ITEMS_BY_RAID = rates.getProperty("RateRaidDropItems", 1.0);      ConfigRates.RATE_DROP_ITEMS_BY_GRAND = rates.getProperty("RateGrandDropItems", 1.0);      ConfigRates.RATE_DROP_SPOIL = rates.getProperty("RateDropSpoil", 1.0);      ConfigRates.PREMIUM_RATE_XP = rates.getProperty("PremiumRateXp", 2.0);      ConfigRates.PREMIUM_RATE_SP = rates.getProperty("PremiumRateSp", 2.0);      ConfigRates.PREMIUM_RATE_DROP_CURRENCY = rates.getProperty("PremiumRateDropCurrency", 2.0);      ConfigRates.PREMIUM_RATE_DROP_SEAL_STONE = rates.getProperty("PremiumRateDropSealStone", 2.0);      ConfigRates.PREMIUM_RATE_DROP_SPOIL = rates.getProperty("PremiumRateDropSpoil", 2.0);      ConfigRates.PREMIUM_RATE_DROP_ITEMS = rates.getProperty("PremiumRateDropItems", 2.0);      ConfigRates.PREMIUM_RATE_DROP_ITEMS_BY_RAID = rates.getProperty("PremiumRateRaidDropItems", 2.0);      ConfigRates.PREMIUM_RATE_DROP_ITEMS_BY_GRAND = rates.getProperty("PremiumRateGrandDropItems", 2.0);      ConfigRates.PREMIUM_RATE_QUEST_DROP = rates.getProperty("PremiumRateQuestDrop", 2.0);      ConfigRates.PREMIUM_RATE_QUEST_REWARD = rates.getProperty("PremiumRateQuestReward", 2.0);      ConfigRates.PREMIUM_RATE_QUEST_REWARD_XP = rates.getProperty("PremiumRateQuestRewardXP", 2.0);      ConfigRates.PREMIUM_RATE_QUEST_REWARD_SP = rates.getProperty("PremiumRateQuestRewardSP", 2.0);      ConfigRates.PREMIUM_RATE_QUEST_REWARD_ADENA = rates.getProperty("PremiumRateQuestRewardAdena", 2.0);      ConfigRates.DYNAMIC_XP = rates.getProperty("DynamicXp", false);      if (ConfigRates.DYNAMIC_XP) {
         ConfigRates.DYNAMIC_XP_RATES = new it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap();         String[] propertySplit = rates.getProperty("DynamicXpRates", "").split(";");

         for (String rate : propertySplit) {
            String[] rateSplit = rate.split(":");
            if (rateSplit.length != 2) {
               Config.LOGGER.warn("[DynamicXpRates]: invalid config property -> DynamicXpRates \"" + rate + "\"");
            } else {
               try {
                  ConfigRates.DYNAMIC_XP_RATES.put(Integer.parseInt(rateSplit[0]), Double.parseDouble(rateSplit[1]));               } catch (NumberFormatException var8) {
                  var8.printStackTrace();
                  if (!rate.equals("")) {
                     Config.LOGGER.warn("[DynamicXpRates]: invalid config property -> DynamicXpRates \"" + rateSplit[0] + "\"" + rateSplit[1]);
                  }
               }
            }
         }
      }

      ConfigRates.RATE_DROP_HERBS = rates.getProperty("RateDropHerbs", 1.0);      ConfigRates.RATE_DROP_MANOR = rates.getProperty("RateDropManor", 1);      ConfigRates.RATE_QUEST_DROP = rates.getProperty("RateQuestDrop", 1.0);      ConfigRates.RATE_QUEST_REWARD = rates.getProperty("RateQuestReward", 1.0);      ConfigRates.RATE_QUEST_REWARD_XP = rates.getProperty("RateQuestRewardXP", 1.0);      ConfigRates.RATE_QUEST_REWARD_SP = rates.getProperty("RateQuestRewardSP", 1.0);      ConfigRates.RATE_QUEST_REWARD_ADENA = rates.getProperty("RateQuestRewardAdena", 1.0);      ConfigRates.RATE_KARMA_EXP_LOST = rates.getProperty("RateKarmaExpLost", 1.0);      ConfigRates.RATE_SIEGE_GUARDS_PRICE = rates.getProperty("RateSiegeGuardsPrice", 1.0);      ConfigRates.PLAYER_DROP_LIMIT = rates.getProperty("PlayerDropLimit", 3);      ConfigRates.PLAYER_RATE_DROP = rates.getProperty("PlayerRateDrop", 5);      ConfigRates.PLAYER_RATE_DROP_ITEM = rates.getProperty("PlayerRateDropItem", 70);      ConfigRates.PLAYER_RATE_DROP_EQUIP = rates.getProperty("PlayerRateDropEquip", 25);      ConfigRates.PLAYER_RATE_DROP_EQUIP_WEAPON = rates.getProperty("PlayerRateDropEquipWeapon", 5);      ConfigRates.PET_XP_RATE = rates.getProperty("PetXpRate", 1.0);      ConfigRates.PET_FOOD_RATE = rates.getProperty("PetFoodRate", 1);      ConfigRates.SINEATER_XP_RATE = rates.getProperty("SinEaterXpRate", 1.0);      ConfigRates.KARMA_DROP_LIMIT = rates.getProperty("KarmaDropLimit", 10);      ConfigRates.KARMA_RATE_DROP = rates.getProperty("KarmaRateDrop", 70);      ConfigRates.KARMA_RATE_DROP_ITEM = rates.getProperty("KarmaRateDropItem", 50);      ConfigRates.KARMA_RATE_DROP_EQUIP = rates.getProperty("KarmaRateDropEquip", 40);      ConfigRates.KARMA_RATE_DROP_EQUIP_WEAPON = rates.getProperty("KarmaRateDropEquipWeapon", 10);      ConfigRates.GRANDBOSS_RATE_XP = rates.getProperty("GrandBossRateXp", 1.0);      ConfigRates.GRANDBOSS_RATE_SP = rates.getProperty("GrandBossRateSp", 1.0);      ConfigRates.RAIDBOSS_RATE_XP = rates.getProperty("RaidBossRateXp", 1.0);      ConfigRates.RAIDBOSS_RATE_SP = rates.getProperty("RaidBossRateSp", 1.0);   }
}
