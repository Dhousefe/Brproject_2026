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
 * Phase 4 config domain: ConfigPlayers.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigPlayers
{
   private ConfigPlayers()
   {
   }

   public static boolean EFFECT_CANCELING;
   public static double HP_REGEN_MULTIPLIER;
   public static double MP_REGEN_MULTIPLIER;
   public static double CP_REGEN_MULTIPLIER;
   public static int PLAYER_SPAWN_PROTECTION;
   public static int PLAYER_FAKEDEATH_UP_PROTECTION;
   public static double RESPAWN_RESTORE_HP;
   public static int MAX_PVTSTOREBUY_SLOTS_DWARF;
   public static int MAX_PVTSTOREBUY_SLOTS_OTHER;
   public static int MAX_PVTSTORESELL_SLOTS_DWARF;
   public static int MAX_PVTSTORESELL_SLOTS_OTHER;
   public static boolean DEEPBLUE_DROP_RULES;
   public static boolean ALLOW_DELEVEL;
   public static int DEATH_PENALTY_CHANCE;
   public static int INVENTORY_MAXIMUM_NO_DWARF;
   public static int INVENTORY_MAXIMUM_DWARF;
   public static int INVENTORY_MAXIMUM_PET;
   public static int MAX_ITEM_IN_PACKET;
   public static double WEIGHT_LIMIT;
   public static int WAREHOUSE_SLOTS_NO_DWARF;
   public static int WAREHOUSE_SLOTS_DWARF;
   public static int WAREHOUSE_SLOTS_CLAN;
   public static int FREIGHT_SLOTS;
   public static boolean REGION_BASED_FREIGHT;
   public static int FREIGHT_PRICE;
   public static boolean SUBCLASS_REQUIRE_MIMIR;
   public static boolean SUBCLASS_REQUIRE_FATE;
   public static int AUGMENTATION_NG_SKILL_CHANCE;
   public static int AUGMENTATION_NG_GLOW_CHANCE;
   public static int AUGMENTATION_MID_SKILL_CHANCE;
   public static int AUGMENTATION_MID_GLOW_CHANCE;
   public static int AUGMENTATION_HIGH_SKILL_CHANCE;
   public static int AUGMENTATION_HIGH_GLOW_CHANCE;
   public static int AUGMENTATION_TOP_SKILL_CHANCE;
   public static int AUGMENTATION_TOP_GLOW_CHANCE;
   public static int AUGMENTATION_BASESTAT_CHANCE;
   public static boolean KARMA_PLAYER_CAN_SHOP;
   public static boolean KARMA_PLAYER_CAN_USE_GK;
   public static boolean KARMA_PLAYER_CAN_TELEPORT;
   public static boolean KARMA_PLAYER_CAN_TRADE;
   public static boolean KARMA_PLAYER_CAN_USE_WH;
   public static boolean KARMA_DROP_GM;
   public static boolean KARMA_AWARD_PK_KILL;
   public static int KARMA_PK_LIMIT;
   public static int[] KARMA_NONDROPPABLE_PET_ITEMS;
   public static int[] KARMA_NONDROPPABLE_ITEMS;
   public static int PVP_NORMAL_TIME;
   public static int PVP_PVP_TIME;
   public static String PARTY_XP_CUTOFF_METHOD;
   public static double PARTY_XP_CUTOFF_PERCENT;
   public static int PARTY_XP_CUTOFF_LEVEL;
   public static int PARTY_RANGE;
   public static int DEFAULT_ACCESS_LEVEL;
   public static boolean GM_HERO_AURA;
   public static boolean GM_STARTUP_INVULNERABLE;
   public static boolean GM_STARTUP_INVISIBLE;
   public static boolean GM_STARTUP_BLOCK_ALL;
   public static boolean GM_STARTUP_AUTO_LIST;
   public static boolean PETITIONING_ALLOWED;
   public static int MAX_PETITIONS_PER_PLAYER;
   public static int MAX_PETITIONS_PENDING;
   public static boolean IS_CRAFTING_ENABLED;
   public static int DWARF_RECIPE_LIMIT;
   public static int COMMON_RECIPE_LIMIT;
   public static boolean AUTO_LEARN_SKILLS;
   public static int LVL_AUTO_LEARN_SKILLS;
   public static boolean MAGIC_FAILURES;
   public static int PERFECT_SHIELD_BLOCK_RATE;
   public static boolean LIFE_CRYSTAL_NEEDED;
   public static boolean SP_BOOK_NEEDED;
   public static boolean ES_SP_BOOK_NEEDED;
   public static boolean DIVINE_SP_BOOK_NEEDED;
   public static boolean SUBCLASS_WITHOUT_QUESTS;
   public static int MAX_BUFFS_AMOUNT;
   public static boolean STORE_SKILL_COOLTIME;
   public static boolean EXPERTISE_PENALTY;

   // DEX Attack Recovery & Movement Reaction Balancing
   public static boolean ENABLE_DEX_ATTACK_RECOVERY_SCALING;
   public static double DEX_ATTACK_RECOVERY_MIN_FACTOR;
   public static double DEX_ATTACK_RECOVERY_MAX_FACTOR;
   public static int DEX_ATTACK_RECOVERY_BASE_DEX;
   public static double DEX_ATTACK_RECOVERY_DEX_DIVISOR;
   public static double DEX_ATTACK_RECOVERY_MAX_BONUS;

   // Magic Skill Inbound Flood Mitigation & ActionFailed Hysteresis
   public static int MAGIC_SKILL_DEBOUNCE_TIME_MS;
   public static int MAGIC_SKILL_QUEUING_WINDOW_MS;
   public static int ACTION_FAILED_MIN_INTERVAL_MS;

   public static void load() {
      ExProperties players = Config.initProperties(Config.PLAYERS_FILE);
      ConfigPlayers.EFFECT_CANCELING = players.getProperty("CancelLesserEffect", true);      ConfigPlayers.HP_REGEN_MULTIPLIER = players.getProperty("HpRegenMultiplier", 1.0);      ConfigPlayers.MP_REGEN_MULTIPLIER = players.getProperty("MpRegenMultiplier", 1.0);      ConfigPlayers.CP_REGEN_MULTIPLIER = players.getProperty("CpRegenMultiplier", 1.0);      ConfigPlayers.PLAYER_SPAWN_PROTECTION = players.getProperty("PlayerSpawnProtection", 0);      ConfigPlayers.PLAYER_FAKEDEATH_UP_PROTECTION = players.getProperty("PlayerFakeDeathUpProtection", 5);      ConfigPlayers.RESPAWN_RESTORE_HP = players.getProperty("RespawnRestoreHP", 0.7);      ConfigPlayers.MAX_PVTSTOREBUY_SLOTS_DWARF = players.getProperty("MaxPvtStoreBuySlotsDwarf", 5);      ConfigPlayers.MAX_PVTSTOREBUY_SLOTS_OTHER = players.getProperty("MaxPvtStoreBuySlotsOther", 4);      ConfigPlayers.MAX_PVTSTORESELL_SLOTS_DWARF = players.getProperty("MaxPvtStoreSellSlotsDwarf", 4);      ConfigPlayers.MAX_PVTSTORESELL_SLOTS_OTHER = players.getProperty("MaxPvtStoreSellSlotsOther", 3);      ConfigPlayers.DEEPBLUE_DROP_RULES = players.getProperty("UseDeepBlueDropRules", true);      ConfigPlayers.ALLOW_DELEVEL = players.getProperty("AllowDelevel", true);      ConfigPlayers.DEATH_PENALTY_CHANCE = players.getProperty("DeathPenaltyChance", 20);      ConfigNpcs.DEBUG_MELEE_ATTACK = players.getProperty("DebugMeleeAttack", ConfigNpcs.DEBUG_MELEE_ATTACK);      ConfigPlayers.INVENTORY_MAXIMUM_NO_DWARF = players.getProperty("MaximumSlotsForNoDwarf", 80);      ConfigPlayers.INVENTORY_MAXIMUM_DWARF = players.getProperty("MaximumSlotsForDwarf", 100);      ConfigPlayers.INVENTORY_MAXIMUM_PET = players.getProperty("MaximumSlotsForPet", 12);      ConfigPlayers.MAX_ITEM_IN_PACKET = Math.max(ConfigPlayers.INVENTORY_MAXIMUM_NO_DWARF, ConfigPlayers.INVENTORY_MAXIMUM_DWARF);      ConfigPlayers.WEIGHT_LIMIT = players.getProperty("WeightLimit", 1.0);      ConfigPlayers.WAREHOUSE_SLOTS_NO_DWARF = players.getProperty("MaximumWarehouseSlotsForNoDwarf", 100);      ConfigPlayers.WAREHOUSE_SLOTS_DWARF = players.getProperty("MaximumWarehouseSlotsForDwarf", 120);      ConfigPlayers.WAREHOUSE_SLOTS_CLAN = players.getProperty("MaximumWarehouseSlotsForClan", 150);      ConfigPlayers.FREIGHT_SLOTS = players.getProperty("MaximumFreightSlots", 20);      ConfigPlayers.REGION_BASED_FREIGHT = players.getProperty("RegionBasedFreight", true);      ConfigPlayers.FREIGHT_PRICE = players.getProperty("FreightPrice", 1000);      ConfigPlayers.SUBCLASS_REQUIRE_MIMIR = players.getProperty("SubclassRequireMimir", true);      ConfigPlayers.SUBCLASS_REQUIRE_FATE = players.getProperty("SubclassRequireFate", true);      ConfigPlayers.AUGMENTATION_NG_SKILL_CHANCE = players.getProperty("AugmentationNGSkillChance", 15);      ConfigPlayers.AUGMENTATION_NG_GLOW_CHANCE = players.getProperty("AugmentationNGGlowChance", 0);      ConfigPlayers.AUGMENTATION_MID_SKILL_CHANCE = players.getProperty("AugmentationMidSkillChance", 30);      ConfigPlayers.AUGMENTATION_MID_GLOW_CHANCE = players.getProperty("AugmentationMidGlowChance", 40);      ConfigPlayers.AUGMENTATION_HIGH_SKILL_CHANCE = players.getProperty("AugmentationHighSkillChance", 45);      ConfigPlayers.AUGMENTATION_HIGH_GLOW_CHANCE = players.getProperty("AugmentationHighGlowChance", 70);      ConfigPlayers.AUGMENTATION_TOP_SKILL_CHANCE = players.getProperty("AugmentationTopSkillChance", 60);      ConfigPlayers.AUGMENTATION_TOP_GLOW_CHANCE = players.getProperty("AugmentationTopGlowChance", 100);      ConfigPlayers.AUGMENTATION_BASESTAT_CHANCE = players.getProperty("AugmentationBaseStatChance", 1);      ConfigPlayers.KARMA_PLAYER_CAN_SHOP = players.getProperty("KarmaPlayerCanShop", false);      ConfigPlayers.KARMA_PLAYER_CAN_USE_GK = players.getProperty("KarmaPlayerCanUseGK", false);      ConfigPlayers.KARMA_PLAYER_CAN_TELEPORT = players.getProperty("KarmaPlayerCanTeleport", true);      ConfigPlayers.KARMA_PLAYER_CAN_TRADE = players.getProperty("KarmaPlayerCanTrade", true);      ConfigPlayers.KARMA_PLAYER_CAN_USE_WH = players.getProperty("KarmaPlayerCanUseWareHouse", true);      ConfigPlayers.KARMA_DROP_GM = players.getProperty("CanGMDropEquipment", false);      ConfigPlayers.KARMA_AWARD_PK_KILL = players.getProperty("AwardPKKillPVPPoint", true);      ConfigPlayers.KARMA_PK_LIMIT = players.getProperty("MinimumPKRequiredToDrop", 5);      ConfigPlayers.KARMA_NONDROPPABLE_PET_ITEMS = players.getProperty("ListOfPetItems", new int[]{2375, 3500, 3501, 3502, 4422, 4423, 4424, 4425, 6648, 6649, 6650});      ConfigPlayers.KARMA_NONDROPPABLE_ITEMS = players.getProperty("ListOfNonDroppableItemsForPK", new int[]{1147, 425, 1146, 461, 10, 2368, 7, 6, 2370, 2369});      ConfigPlayers.PVP_NORMAL_TIME = players.getProperty("PvPVsNormalTime", 40000);      ConfigPlayers.PVP_PVP_TIME = players.getProperty("PvPVsPvPTime", 20000);      ConfigPlayers.PARTY_XP_CUTOFF_METHOD = players.getProperty("PartyXpCutoffMethod", "level");      ConfigPlayers.PARTY_XP_CUTOFF_PERCENT = players.getProperty("PartyXpCutoffPercent", 3.0);      ConfigPlayers.PARTY_XP_CUTOFF_LEVEL = players.getProperty("PartyXpCutoffLevel", 20);      ConfigPlayers.PARTY_RANGE = players.getProperty("PartyRange", 1500);      ConfigPlayers.DEFAULT_ACCESS_LEVEL = players.getProperty("DefaultAccessLevel", 0);      ConfigPlayers.GM_HERO_AURA = players.getProperty("GMHeroAura", false);      ConfigPlayers.GM_STARTUP_INVULNERABLE = players.getProperty("GMStartupInvulnerable", false);      ConfigPlayers.GM_STARTUP_INVISIBLE = players.getProperty("GMStartupInvisible", false);      ConfigPlayers.GM_STARTUP_BLOCK_ALL = players.getProperty("GMStartupBlockAll", false);      ConfigPlayers.GM_STARTUP_AUTO_LIST = players.getProperty("GMStartupAutoList", true);      ConfigPlayers.PETITIONING_ALLOWED = players.getProperty("PetitioningAllowed", true);      ConfigPlayers.MAX_PETITIONS_PER_PLAYER = players.getProperty("MaxPetitionsPerPlayer", 5);      ConfigPlayers.MAX_PETITIONS_PENDING = players.getProperty("MaxPetitionsPending", 25);      ConfigPlayers.IS_CRAFTING_ENABLED = players.getProperty("CraftingEnabled", true);      ConfigPlayers.DWARF_RECIPE_LIMIT = players.getProperty("DwarfRecipeLimit", 50);      ConfigPlayers.COMMON_RECIPE_LIMIT = players.getProperty("CommonRecipeLimit", 50);      ConfigPlayers.AUTO_LEARN_SKILLS = players.getProperty("AutoLearnSkills", false);      ConfigPlayers.LVL_AUTO_LEARN_SKILLS = players.getProperty("LvlAutoLearnSkills", 40);      ConfigPlayers.MAGIC_FAILURES = players.getProperty("MagicFailures", true);      ConfigPlayers.PERFECT_SHIELD_BLOCK_RATE = players.getProperty("PerfectShieldBlockRate", 5);      ConfigPlayers.LIFE_CRYSTAL_NEEDED = players.getProperty("LifeCrystalNeeded", true);      ConfigPlayers.SP_BOOK_NEEDED = players.getProperty("SpBookNeeded", true);      ConfigPlayers.ES_SP_BOOK_NEEDED = players.getProperty("EnchantSkillSpBookNeeded", true);      ConfigPlayers.DIVINE_SP_BOOK_NEEDED = players.getProperty("DivineInspirationSpBookNeeded", true);      ConfigPlayers.SUBCLASS_WITHOUT_QUESTS = players.getProperty("SubClassWithoutQuests", false);      ConfigPlayers.MAX_BUFFS_AMOUNT = players.getProperty("MaxBuffsAmount", 20);      ConfigPlayers.STORE_SKILL_COOLTIME = players.getProperty("StoreSkillCooltime", true);      ConfigPlayers.EXPERTISE_PENALTY = players.getProperty("ExpertisePenalty", true);
      ConfigPlayers.ENABLE_DEX_ATTACK_RECOVERY_SCALING = players.getProperty("EnableDexAttackRecoveryScaling", true);
      ConfigPlayers.DEX_ATTACK_RECOVERY_MIN_FACTOR = players.getProperty("DexAttackRecoveryMinFactor", 0.50);
      ConfigPlayers.DEX_ATTACK_RECOVERY_MAX_FACTOR = players.getProperty("DexAttackRecoveryMaxFactor", 1.15);
      ConfigPlayers.DEX_ATTACK_RECOVERY_BASE_DEX = players.getProperty("DexAttackRecoveryBaseDex", 20);
      ConfigPlayers.DEX_ATTACK_RECOVERY_DEX_DIVISOR = players.getProperty("DexAttackRecoveryDexDivisor", 45.0);
      ConfigPlayers.DEX_ATTACK_RECOVERY_MAX_BONUS = players.getProperty("DexAttackRecoveryMaxBonus", 0.45);
      ConfigPlayers.MAGIC_SKILL_DEBOUNCE_TIME_MS = players.getProperty("MagicSkillDebounceTimeMs", 200);
      ConfigPlayers.MAGIC_SKILL_QUEUING_WINDOW_MS = players.getProperty("MagicSkillQueuingWindowMs", 300);
      ConfigPlayers.ACTION_FAILED_MIN_INTERVAL_MS = players.getProperty("ActionFailedMinIntervalMs", 250);
   }
}
