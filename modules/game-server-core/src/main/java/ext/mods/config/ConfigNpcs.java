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
 * Phase 4 config domain: ConfigNpcs.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigNpcs
{
   private ConfigNpcs()
   {
   }

   public static double SPAWN_MULTIPLIER;
   public static String[] SPAWN_EVENTS;
   public static int MONSTER_MAX_RANGE;
   public static int CHAMPION_FREQUENCY;
   public static int CHAMP_MIN_LVL;
   public static int CHAMP_MAX_LVL;
   public static int CHAMPION_HP;
   public static double CHAMPION_HP_REGEN;
   public static double CHAMPION_RATE_XP;
   public static double CHAMPION_RATE_SP;
   public static double PREMIUM_CHAMPION_RATE_XP;
   public static double PREMIUM_CHAMPION_RATE_SP;
   public static int CHAMPION_REWARDS;
   public static int PREMIUM_CHAMPION_REWARDS;
   public static int CHAMPION_ADENAS_REWARDS;
   public static int CHAMPION_SEALSTONE_REWARDS;
   public static int PREMIUM_CHAMPION_ADENAS_REWARDS;
   public static int PREMIUM_CHAMPION_SEALSTONE_REWARDS;
   public static int CHAMPION_SPOIL_REWARDS;
   public static int PREMIUM_CHAMPION_SPOIL_REWARDS;
   public static double CHAMPION_ATK;
   public static double CHAMPION_MATK;
   public static double CHAMPION_SPD_ATK;
   public static double CHAMPION_SPD_MATK;
   public static int CHAMPION_REWARD;
   public static int CHAMPION_REWARD_ID;
   public static int CHAMPION_REWARD_QTY;
   public static int CHAMPION_AURA;
   public static boolean ALLOW_ENTIRE_TREE;
   public static ext.mods.Config.ClassMasterSettings CLASS_MASTER_SETTINGS;
   public static boolean ALTERNATE_CLASS_MASTER;
   public static int NOBLE_ITEM_ID;
   public static int NOBLE_ITEM_COUNT;
   public static int WEDDING_PRICE;
   public static boolean WEDDING_SAMESEX;
   public static boolean WEDDING_FORMALWEAR;
   public static int BUFFER_MAX_SCHEMES;
   public static int BUFFER_STATIC_BUFF_COST;
   public static boolean FREE_TELEPORT;
   public static int LVL_FREE_TELEPORT;
   public static boolean ANNOUNCE_MAMMON_SPAWN;
   public static boolean MOB_AGGRO_IN_PEACEZONE;
   public static boolean SHOW_NPC_LVL;
   public static String SHOW_NPC_TITLE_FORMAT;
   public static String SHOW_NPC_TITLE_COLOR;
   public static boolean SHOW_NPC_CREST;
   public static boolean SHOW_SUMMON_CREST;
   public static int WYVERN_REQUIRED_LEVEL;
   public static int WYVERN_REQUIRED_CRYSTALS;
   public static boolean NPC_STAT_MULTIPLIERS;
   public static double MONSTER_HP_MULTIPLIER;
   public static double MONSTER_MP_MULTIPLIER;
   public static double MONSTER_PATK_MULTIPLIER;
   public static double MONSTER_MATK_MULTIPLIER;
   public static double MONSTER_PDEF_MULTIPLIER;
   public static double MONSTER_MDEF_MULTIPLIER;
   public static double RAIDBOSS_HP_MULTIPLIER;
   public static double RAIDBOSS_MP_MULTIPLIER;
   public static double RAIDBOSS_PATK_MULTIPLIER;
   public static double RAIDBOSS_MATK_MULTIPLIER;
   public static double RAIDBOSS_PDEF_MULTIPLIER;
   public static double RAIDBOSS_MDEF_MULTIPLIER;
   public static double GRANDBOSS_HP_MULTIPLIER;
   public static double GRANDBOSS_MP_MULTIPLIER;
   public static double GRANDBOSS_PATK_MULTIPLIER;
   public static double GRANDBOSS_MATK_MULTIPLIER;
   public static double GRANDBOSS_PDEF_MULTIPLIER;
   public static double GRANDBOSS_MDEF_MULTIPLIER;
   public static boolean RAID_DISABLE_CURSE;
   public static int WAIT_TIME_ANTHARAS;
   public static boolean NEED_ITEM_ANTHARAS;
   public static int WAIT_TIME_VALAKAS;
   public static boolean NEED_ITEM_VALAKAS;
   public static int WAIT_TIME_FRINTEZZA;
   public static int FRINTEZZA_MINIMUM_ALLOWED_PLAYERS;
   public static int FRINTEZZA_MAXIMUM_ALLOWED_PLAYERS;
   public static int FRINTEZZA_MINIMUM_PARTIES;
   public static int FRINTEZZA_MAXIMUM_PARTIES;
   public static boolean NEED_ITEM_FRINTEZZA;
   public static boolean NEED_ITEM_BAIUM;
   public static boolean NEED_ITEM_SHILEN;
   public static boolean GUARD_ATTACK_AGGRO_MOB;
   public static int RANDOM_WALK_RATE;
   public static int MAX_DRIFT_RANGE;
   public static boolean DEBUG_MELEE_ATTACK;
   public static boolean ENABLE_GUARD_CHAT;
   public static int GUARD_CHAT_RANGE;
   public static int GUARD_CHATTY_CHANCE;
   public static int GUARD_SAY_NORMAL_CHANCE;
   public static long GUARD_SAY_NORMAL_PERIOD;
   public static long GUARD_SAY_AGGRO_PERIOD;
   public static int NPC_ANIMATION;
   public static int MONSTER_ANIMATION;
   public static int DEFAULT_SEE_RANGE;
   public static int SUMMON_DRIFT_RANGE;
   public static int[] RAID_BOSS_LIST;
   public static int[] EPIC_BOSS_LIST;

   public static void load() {
      ExProperties npcs = Config.initProperties(Config.NPCS_FILE);
      ConfigNpcs.SPAWN_MULTIPLIER = npcs.getProperty("SpawnMultiplier", 1.0);      ConfigNpcs.SPAWN_EVENTS = npcs.getProperty("SpawnEvents", new String[]{"extra_mob", "18age", "start_weapon"});      ConfigNpcs.MONSTER_MAX_RANGE = npcs.getProperty("MonsterMaxRange", 15);      ConfigNpcs.CHAMPION_FREQUENCY = npcs.getProperty("ChampionFrequency", 0);      ConfigNpcs.CHAMP_MIN_LVL = npcs.getProperty("ChampionMinLevel", 20);      ConfigNpcs.CHAMP_MAX_LVL = npcs.getProperty("ChampionMaxLevel", 70);      ConfigNpcs.CHAMPION_HP = npcs.getProperty("ChampionHp", 8);      ConfigNpcs.CHAMPION_HP_REGEN = npcs.getProperty("ChampionHpRegen", 1.0);      ConfigNpcs.CHAMPION_RATE_XP = npcs.getProperty("ChampionRateXp", 1.0);      ConfigNpcs.CHAMPION_RATE_SP = npcs.getProperty("ChampionRateSp", 1.0);      ConfigNpcs.PREMIUM_CHAMPION_RATE_XP = npcs.getProperty("PremiumChampionRateXp", 1.0);      ConfigNpcs.PREMIUM_CHAMPION_RATE_SP = npcs.getProperty("PremiumChampionRateSp", 1.0);      ConfigNpcs.CHAMPION_REWARDS = npcs.getProperty("ChampionRewards", 1);      ConfigNpcs.PREMIUM_CHAMPION_REWARDS = npcs.getProperty("PremiumChampionRewards", 1);      ConfigNpcs.CHAMPION_ADENAS_REWARDS = npcs.getProperty("ChampionAdenasRewards", 1);      ConfigNpcs.CHAMPION_SEALSTONE_REWARDS = npcs.getProperty("ChampionSealStoneRewards", 1);      ConfigNpcs.PREMIUM_CHAMPION_ADENAS_REWARDS = npcs.getProperty("PremiumChampionAdenasRewards", 1);      ConfigNpcs.PREMIUM_CHAMPION_SEALSTONE_REWARDS = npcs.getProperty("PremiumChampionSealStoneRewards", 1);      ConfigNpcs.CHAMPION_SPOIL_REWARDS = npcs.getProperty("ChampionSpoilRewards", 1);      ConfigNpcs.PREMIUM_CHAMPION_SPOIL_REWARDS = npcs.getProperty("PremiumChampionSpoilRewards", 1);      ConfigNpcs.CHAMPION_ATK = npcs.getProperty("ChampionAtk", 1.0);      ConfigNpcs.CHAMPION_MATK = npcs.getProperty("ChampionMAtk", 1.0);      ConfigNpcs.CHAMPION_SPD_ATK = npcs.getProperty("ChampionSpdAtk", 1.0);      ConfigNpcs.CHAMPION_SPD_MATK = npcs.getProperty("ChampionSpdMAtk", 1.0);      ConfigNpcs.CHAMPION_REWARD = npcs.getProperty("ChampionRewardItem", 0);      ConfigNpcs.CHAMPION_REWARD_ID = npcs.getProperty("ChampionRewardItemID", 6393);      ConfigNpcs.CHAMPION_REWARD_QTY = npcs.getProperty("ChampionRewardItemQty", 1);      ConfigNpcs.CHAMPION_AURA = npcs.getProperty("ChampionAura", 0);      ConfigNpcs.ALLOW_ENTIRE_TREE = npcs.getProperty("AllowEntireTree", false);      ConfigNpcs.CLASS_MASTER_SETTINGS = new ext.mods.Config.ClassMasterSettings(npcs.getProperty("ConfigClassMaster"));      ConfigNpcs.ALTERNATE_CLASS_MASTER = npcs.getProperty("AlternateClassMaster", true);      ConfigNpcs.NOBLE_ITEM_ID = npcs.getProperty("NobleItemId", 4037);      ConfigNpcs.NOBLE_ITEM_COUNT = npcs.getProperty("NobleItemCount", 50);      ConfigNpcs.WEDDING_PRICE = npcs.getProperty("WeddingPrice", 1000000);      ConfigNpcs.WEDDING_SAMESEX = npcs.getProperty("WeddingAllowSameSex", false);      ConfigNpcs.WEDDING_FORMALWEAR = npcs.getProperty("WeddingFormalWear", true);      ConfigNpcs.BUFFER_MAX_SCHEMES = npcs.getProperty("BufferMaxSchemesPerChar", 4);      ConfigNpcs.BUFFER_STATIC_BUFF_COST = npcs.getProperty("BufferStaticCostPerBuff", -1);      ConfigNpcs.FREE_TELEPORT = npcs.getProperty("FreeTeleport", false);      ConfigNpcs.LVL_FREE_TELEPORT = npcs.getProperty("LvlFreeTeleport", 40);      ConfigNpcs.ANNOUNCE_MAMMON_SPAWN = npcs.getProperty("AnnounceMammonSpawn", false);      ConfigNpcs.MOB_AGGRO_IN_PEACEZONE = npcs.getProperty("MobAggroInPeaceZone", true);      ConfigNpcs.SHOW_NPC_LVL = npcs.getProperty("ShowNpcLevel", false);      ConfigNpcs.SHOW_NPC_TITLE_FORMAT = npcs.getProperty("ShowNpcTitleFormat", "Lvl %level% %aggro% %title%");      ConfigNpcs.SHOW_NPC_TITLE_COLOR = npcs.getProperty("ShowNpcTitleColor", "");      ConfigNpcs.SHOW_NPC_CREST = npcs.getProperty("ShowNpcCrest", false);      ConfigNpcs.SHOW_SUMMON_CREST = npcs.getProperty("ShowSummonCrest", false);      ConfigNpcs.WYVERN_REQUIRED_LEVEL = npcs.getProperty("RequiredStriderLevel", 55);      ConfigNpcs.WYVERN_REQUIRED_CRYSTALS = npcs.getProperty("RequiredCrystalsNumber", 10);      ConfigNpcs.NPC_STAT_MULTIPLIERS = npcs.getProperty("NpcStatMultipliers", false);      ConfigNpcs.MONSTER_HP_MULTIPLIER = npcs.getProperty("MonsterHP", 10.0);      ConfigNpcs.MONSTER_MP_MULTIPLIER = npcs.getProperty("MonsterMP", 10.0);      ConfigNpcs.MONSTER_PATK_MULTIPLIER = npcs.getProperty("MonsterPAtk", 10.0);      ConfigNpcs.MONSTER_MATK_MULTIPLIER = npcs.getProperty("MonsterMAtk", 10.0);      ConfigNpcs.MONSTER_PDEF_MULTIPLIER = npcs.getProperty("MonsterPDef", 10.0);      ConfigNpcs.MONSTER_MDEF_MULTIPLIER = npcs.getProperty("MonsterMDef", 10.0);      ConfigNpcs.RAIDBOSS_HP_MULTIPLIER = npcs.getProperty("RaidbossHP", 1.0);      ConfigNpcs.RAIDBOSS_MP_MULTIPLIER = npcs.getProperty("RaidbossMP", 1.0);      ConfigNpcs.RAIDBOSS_PATK_MULTIPLIER = npcs.getProperty("RaidbossPAtk", 1.0);      ConfigNpcs.RAIDBOSS_MATK_MULTIPLIER = npcs.getProperty("RaidbossMAtk", 1.0);      ConfigNpcs.RAIDBOSS_PDEF_MULTIPLIER = npcs.getProperty("RaidbossPDef", 1.0);      ConfigNpcs.RAIDBOSS_MDEF_MULTIPLIER = npcs.getProperty("RaidbossMDef", 1.0);      ConfigNpcs.GRANDBOSS_HP_MULTIPLIER = npcs.getProperty("GrandbossHP", 1.0);      ConfigNpcs.GRANDBOSS_MP_MULTIPLIER = npcs.getProperty("GrandbossMP", 1.0);      ConfigNpcs.GRANDBOSS_PATK_MULTIPLIER = npcs.getProperty("GrandbossPAtk", 1.0);      ConfigNpcs.GRANDBOSS_MATK_MULTIPLIER = npcs.getProperty("GrandbossMAtk", 1.0);      ConfigNpcs.GRANDBOSS_PDEF_MULTIPLIER = npcs.getProperty("GrandbossPDef", 1.0);      ConfigNpcs.GRANDBOSS_MDEF_MULTIPLIER = npcs.getProperty("GrandbossMDef", 1.0);      ConfigNpcs.RAID_DISABLE_CURSE = npcs.getProperty("DisableRaidCurse", false);      ConfigNpcs.WAIT_TIME_ANTHARAS = npcs.getProperty("AntharasWaitTime", 30) * 60000;      ConfigNpcs.NEED_ITEM_ANTHARAS = npcs.getProperty("AntharasNeedItem", true);      ConfigNpcs.WAIT_TIME_VALAKAS = npcs.getProperty("ValakasWaitTime", 20) * 60000;      ConfigNpcs.NEED_ITEM_VALAKAS = npcs.getProperty("ValakasNeedItem", true);      ConfigNpcs.WAIT_TIME_FRINTEZZA = npcs.getProperty("FrintezzaWaitTime", 10) * 60000;      ConfigNpcs.FRINTEZZA_MINIMUM_ALLOWED_PLAYERS = npcs.getProperty("FrintezzaMinimumAllowedPlayers", 1);      ConfigNpcs.FRINTEZZA_MAXIMUM_ALLOWED_PLAYERS = npcs.getProperty("FrintezzaMaximumAllowedPlayers", 99);      ConfigNpcs.FRINTEZZA_MINIMUM_PARTIES = npcs.getProperty("FrintezzaMinimumParties", 4);      ConfigNpcs.FRINTEZZA_MAXIMUM_PARTIES = npcs.getProperty("FrintezzaMaximumParties", 5);      ConfigNpcs.NEED_ITEM_FRINTEZZA = npcs.getProperty("FrintezzaNeedItem", true);      ConfigNpcs.NEED_ITEM_BAIUM = npcs.getProperty("BaiumNeedItem", true);      ConfigNpcs.NEED_ITEM_SHILEN = npcs.getProperty("ShilenNeedItem", false);      ConfigNpcs.GUARD_ATTACK_AGGRO_MOB = npcs.getProperty("GuardAttackAggroMob", false);      ConfigNpcs.RANDOM_WALK_RATE = npcs.getProperty("RandomWalkRate", 30);      ConfigNpcs.MAX_DRIFT_RANGE = npcs.getProperty("MaxDriftRange", 200);      ConfigNpcs.DEBUG_MELEE_ATTACK = npcs.getProperty("DebugMeleeAttack", false);      ConfigNpcs.ENABLE_GUARD_CHAT = npcs.getProperty("EnableGuardChat", true);      ConfigNpcs.GUARD_CHAT_RANGE = npcs.getProperty("GuardChatRange", 300);      ConfigNpcs.GUARD_CHATTY_CHANCE = npcs.getProperty("GuardChattyChance", 50);      ConfigNpcs.GUARD_SAY_NORMAL_CHANCE = npcs.getProperty("GuardSayNormalChance", 5);      ConfigNpcs.GUARD_SAY_NORMAL_PERIOD = (long)(npcs.getProperty("GuardSayNormalPeriod", 120) * 1000);      ConfigNpcs.GUARD_SAY_AGGRO_PERIOD = (long)(npcs.getProperty("GuardSayAggroPeriod", 10) * 1000);      ConfigNpcs.NPC_ANIMATION = npcs.getProperty("NpcAnimation", 40);      ConfigNpcs.MONSTER_ANIMATION = npcs.getProperty("MonsterAnimation", 20);      ConfigNpcs.DEFAULT_SEE_RANGE = npcs.getProperty("DefaultSeeRange", 450);      ConfigNpcs.SUMMON_DRIFT_RANGE = npcs.getProperty("SummonDriftRange", 70);      ConfigNpcs.RAID_BOSS_LIST = npcs.getProperty("RaidBossList", new int[]{0});      ConfigNpcs.EPIC_BOSS_LIST = npcs.getProperty("EpicBossList", new int[]{0});   }
}
