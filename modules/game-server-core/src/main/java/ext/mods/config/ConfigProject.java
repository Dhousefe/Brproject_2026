package ext.mods.config;

import java.util.stream.Stream;

import java.nio.file.Paths;

import java.nio.file.Files;

import java.io.IOException;

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
 * Phase 4 config domain: ConfigProject.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigProject
{
   private ConfigProject()
   {
   }

   public static boolean INFINITY_SS;
   public static boolean INFINITY_ARROWS;
   public static boolean INFINITY_SS_PREMIUM_ONLY;
   public static boolean INFINITY_ARROWS_PREMIUM_ONLY;
   public static boolean OLY_USE_CUSTOM_PERIOD_SETTINGS;
   public static boolean OLY_BLOCK_SAME_HWID;
   public static OlympiadPeriod OLY_PERIOD;
   public static int OLY_PERIOD_MULTIPLIER;
   public static boolean ENABLE_MODIFY_SKILL_DURATION;
   public static HashMap<Integer, Integer> SKILL_DURATION_LIST;
   public static String GLOBAL_CHAT;
   public static String TRADE_CHAT;
   public static int CHAT_ALL_LEVEL;
   public static int CHAT_TELL_LEVEL;
   public static int CHAT_SHOUT_LEVEL;
   public static int CHAT_TRADE_LEVEL;
   public static boolean ENABLE_MENU;
   public static boolean PROP_STOP_EXP;
   public static boolean PROP_TRADE_REFUSAL;
   public static boolean PROP_AUTO_LOOT;
   public static boolean PROP_BUFF_PROTECTED;
   public static boolean ENABLE_ONLINE_COMMAND;
   public static int MULTIPLIER_ONLINE_COMMAND;
   public static boolean BOTS_PREVENTION;
   public static boolean BOTS_LOGS;
   public static int KILLS_COUNTER;
   public static int KILLS_COUNTER_RANDOMIZATION;
   public static int VALIDATION_TIME;
   public static int PUNISHMENT;
   public static int PUNISHMENT_TIME;
   public static boolean USE_PREMIUM_SERVICE;
   public static boolean ALTERNATE_DROP_LIST;
   public static boolean ATTACK_PTS;
   public static boolean SUBCLASS_SKILLS;
   public static boolean GAME_SUBCLASS_EVERYWHERE;
   public static boolean SHOW_NPC_INFO;
   public static boolean ALLOW_GRAND_BOSSES_TELEPORT;
   public static boolean USE_SAY_FILTER;
   public static String CHAT_FILTER_CHARS;
   public static List<String> FILTER_LIST;
   public static boolean CABAL_BUFFER;
   public static boolean SUPER_HASTE;
   public static String RESTRICTED_CHAR_NAMES;
   public static List<String> LIST_RESTRICTED_CHAR_NAMES = new ArrayList<>();
   public static int FAKE_ONLINE_AMOUNT;
   public static String BUFFS_CATEGORY;
   public static List<String> PREMIUM_BUFFS_CATEGORY = new ArrayList<>();
   public static boolean ANTIFEED_ENABLE;
   public static boolean ANTIFEED_DUALBOX;
   public static boolean ANTIFEED_DISCONNECTED_AS_DUALBOX;
   public static int ANTIFEED_INTERVAL;
   public static int DUALBOX_CHECK_MAX_PLAYERS_PER_IP;
   public static int DUALBOX_CHECK_MAX_OLYMPIAD_PARTICIPANTS_PER_IP;
   public static it.unimi.dsi.fastutil.ints.IntSet AUTO_LOOT_ITEM_IDS = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
   public static boolean HIT_TIME;
   public static boolean SHOW_RAID_HTM;
   public static boolean SHOW_EPIC_HTM;
   public static boolean USE_CONFIG_RAID_GRAND_BOSS_RESPAWN;
   public static long RAID_BOSS_RESPAWN_BASE_MS;
   public static long RAID_BOSS_RESPAWN_RANDOM_MS;
   public static long GRAND_BOSS_RESPAWN_BASE_MS;
   public static long GRAND_BOSS_RESPAWN_RANDOM_MS;
   public static Map<Integer, long[]> RAID_BOSS_RESPAWN_OVERRIDES;
   public static Map<Integer, long[]> GRAND_BOSS_RESPAWN_OVERRIDES;
   public static boolean RAID_BOSS_TELEPORT_ENABLED;
   public static boolean EPIC_BOSS_TELEPORT_ENABLED;
   public static int BOSS_TELEPORT_MIN_RANGE;
   public static int BOSS_TELEPORT_MAX_RANGE;
   public static int BOSS_TELEPORT_ITEM_ID;
   public static long BOSS_TELEPORT_ITEM_COUNT;
   public static String TIME_ZONE;
   public static String DATE_FORMAT;
   public static boolean CUSTOM_BUFFER_MANAGER_NPC;
   public static String[] SKIP_CATEGORY;
   public static boolean BARAKIEL;
   public static boolean CREATURE_SEE;
   public static boolean NEW_REGEN;
   public static boolean CATACOMBS_IN_ANY_PERIOD;
   public static boolean STRICT_SEVENSIGNS;
   public static boolean CLASS_OVERLORD;
   public static boolean RACE_ELF;
   public static boolean RESTRICTED_CLASSES;
   public static boolean ENABLE_COMMAND_GOLDBAR;
   public static int BANKING_SYSTEM_GOLDBARS;
   public static int BANKING_SYSTEM_ADENA;
   public static boolean AUTO_POTIONS_ENABLED;
   public static boolean AUTO_POTIONS_IN_OLYMPIAD;
   public static int AUTO_POTION_MIN_LEVEL;
   public static int ACP_PERIOD;
   public static boolean AUTO_CP_ENABLED;
   public static boolean AUTO_HP_ENABLED;
   public static boolean AUTO_MP_ENABLED;
   public static Set<Integer> AUTO_CP_ITEM_IDS;
   public static Set<Integer> AUTO_HP_ITEM_IDS;
   public static Set<Integer> AUTO_MP_ITEM_IDS;
   public static int MULTISELL_MAX_AMOUNT;
   public static boolean ENABLED_AUCTION;
   public static int AUCTION_LIMIT_ITEM;
   public static int AUCTION_FEE;
   public static int AUCTION_ITEM_FEE;
   public static String AUCTION_ITEM_FEE_NAME;
   public static boolean AUTOFARM_ENABLED;
   public static int AUTOFARM_MAX_ZONE_AREA;
   public static int AUTOFARM_MAX_ROUTE_PERIMITER;
   public static int AUTOFARM_MAX_OPEN_RADIUS;
   public static int AUTOFARM_MAX_ZONES;
   public static int AUTOFARM_MAX_ROUTES;
   public static int AUTOFARM_MAX_ZONE_NODES;
   public static int AUTOFARM_MAX_ROUTE_NODES;
   public static int AUTOFARM_MAX_TIMER;
   public static int AUTOFARM_DAILY_LIMIT_HOURS;
   public static int AUTOFARM_RESET_CYCLE_HOURS;
   public static boolean AUTOFARM_PREMIUM_UNLIMITED;
   public static double AUTOFARM_HP_HEAL_RATE;
   public static double AUTOFARM_MP_HEAL_RATE;
   public static int AUTOFARM_DEBUFF_CHANCE;
   public static int[] AUTOFARM_HP_POTIONS;
   public static int[] AUTOFARM_MP_POTIONS;
   public static boolean AUTOFARM_ALLOW_DUALBOX;
   public static boolean AUTOFARM_DISABLE_TOWN;
   public static boolean AUTOFARM_SEND_LOG_MESSAGES;
   public static boolean AUTOFARM_CHANGE_PLAYER_TITLE;
   public static boolean AUTOFARM_CHANGE_PLAYER_NAME_COLOR;
   public static String AUTOFARM_PLAYER_NAME_COLOR;
   public static boolean AUTOFARM_DEBUG_RETURN;
   public static boolean ENABLE_OFFLINE_FARM_COMMAND;
   public static boolean OFFLINE_FARM_PREMIUM;
   public static boolean OFFLINE_FARM_LOGOUT_ON_DEATH;
   public static int DUALBOX_CHECK_MAX_OFFLINEPLAY_PER_IP;
   public static int DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP;
   public static boolean DUALBOX_COUNT_OFFLINE_TRADERS;
   public static boolean AWAY_PLAY_SET_NAME_COLOR;
   public static String AWAY_PLAY_NAME_COLOR;
   public static int HP_POTION_ITEM_ID;
   public static int MP_POTION_ITEM_ID;
   public static int AUTOFARM_MAX_LEVEL_DIFFERENCE;
   public static boolean IGNORE_RAID_BOSSES;
   public static boolean IGNORE_RAID_MINIONS;
   public static boolean IGNORE_AGATHIONS;
   public static boolean IGNORE_CHESTS;
   public static boolean SELLBUFF_ENABLED;
   public static int SELLBUFF_MP_MULTIPLER;
   public static int SELLBUFF_PAYMENT_ID;
   public static long SELLBUFF_MIN_PRICE;
   public static long SELLBUFF_MAX_PRICE;
   public static int SELLBUFF_MAX_BUFFS;
   public static boolean CUSTOM_TIME_BUFF;
   public static boolean ENTER_ANAKAZEL;
   public static int MAX_RUN_SPEED;
   public static int MAX_PATK;
   public static int MAX_MATK;
   public static int MAX_PCRIT_RATE;
   public static int MAX_MCRIT_RATE;
   public static int MAX_PATK_SPEED;
   public static int MAX_MATK_SPEED;
   public static int MAX_EVASION;
   public static boolean NEW_FOLLOW;
   public static boolean ENABLE_MISSION;
   public static boolean RANDOM_PVP_ZONE;
   public static boolean PTS_EMULATION_SPAWN;
   public static int PTS_EMULATION_SPAWN_DURATION;
   public static boolean STOP_TOGGLE;
   public static boolean ANNOUNCE_DIE_RAIDBOSS;
   public static boolean ANNOUNCE_SPAWN_RAIDBOSS;
   public static boolean ANNOUNCE_DIE_GRANDBOSS;
   public static boolean ANNOUNCE_SPAWN_GRANDBOSS;
   public static boolean NPC_SOULSHOT;
   public static boolean NPC_SPIRITSHOT;
   public static boolean RETURN_HOME_MONSTER;
   public static int RETURN_HOME_MONSTER_RADIUS;
   public static boolean RETURN_HOME_RAIDBOSS;
   public static int RETURN_HOME_RAIDBOSS_RADIUS;
   public static boolean CANCEL_RETURN_ENABLED;
   public static String CANCEL_RETURN_MODE;
   public static boolean CANCEL_RETURN_MASS_ONLY;
   public static int CANCEL_RETURN_MASS_MIN_COUNT;
   public static int CANCEL_RETURN_TIME_MS;
   public static boolean CANCEL_RETURN_SKIP_OLYMPIAD;
   public static boolean CANCEL_RETURN_NOTIFY;
   public static String CANCEL_RETURN_MESSAGE;

   public static void load() {
      ExProperties rusacis = Config.initProperties(Config.BR_FILE);
      ConfigProject.INFINITY_SS = rusacis.getProperty("InfinitySS", false);      ConfigProject.INFINITY_ARROWS = rusacis.getProperty("InfinityArrows", false);      ConfigProject.INFINITY_SS_PREMIUM_ONLY = rusacis.getProperty("InfinitySSPremiumOnly", false);      ConfigProject.INFINITY_ARROWS_PREMIUM_ONLY = rusacis.getProperty("InfinityArrowsPremiumOnly", false);      ConfigProject.OLY_USE_CUSTOM_PERIOD_SETTINGS = rusacis.getProperty("OlyUseCustomPeriodSettings", false);      ConfigEvents.OLY_CLASSED = rusacis.getProperty("OlyClassedParticipants", ConfigEvents.OLY_CLASSED);      ConfigEvents.OLY_NONCLASSED = rusacis.getProperty("OlyNonClassedParticipants", ConfigEvents.OLY_NONCLASSED);      ConfigProject.OLY_BLOCK_SAME_HWID = rusacis.getProperty("OlyBlockSameHwid", true);      ConfigProject.OLY_PERIOD = OlympiadPeriod.valueOf(rusacis.getProperty("OlyPeriod", "MONTH"));      ConfigProject.OLY_PERIOD_MULTIPLIER = rusacis.getProperty("OlyPeriodMultiplier", 1);      ConfigProject.ENABLE_MODIFY_SKILL_DURATION = rusacis.getProperty("EnableModifySkillDuration", true);      ConfigProject.SKILL_DURATION_LIST = new HashMap<>();      String[] propertySplit = rusacis.getProperty("SkillDurationList", "").split(";");

      for (String skill : propertySplit) {
         String[] skillSplit = skill.split(",");
         if (skillSplit.length != 2) {
            Config.LOGGER.warn("[SkillDurationList]: invalid config property -> SkillDurationList \"" + skill + "\"");
         } else {
            try {
               ConfigProject.SKILL_DURATION_LIST.put(Integer.parseInt(skillSplit[0]), Integer.parseInt(skillSplit[1]));            } catch (NumberFormatException var13) {
               var13.printStackTrace();
               if (!skill.equals("")) {
                  Config.LOGGER.warn("[SkillDurationList]: invalid config property -> SkillList \"" + skillSplit[0] + "\"" + skillSplit[1]);
               }
            }
         }
      }

      ConfigProject.GLOBAL_CHAT = rusacis.getProperty("GlobalChat", "ON");      ConfigProject.TRADE_CHAT = rusacis.getProperty("TradeChat", "ON");      ConfigProject.CHAT_ALL_LEVEL = rusacis.getProperty("AllChatLevel", 1);      ConfigProject.CHAT_TELL_LEVEL = rusacis.getProperty("TellChatLevel", 1);      ConfigProject.CHAT_SHOUT_LEVEL = rusacis.getProperty("ShoutChatLevel", 1);      ConfigProject.CHAT_TRADE_LEVEL = rusacis.getProperty("TradeChatLevel", 1);      ConfigProject.ENABLE_MENU = rusacis.getProperty("EnableMenu", false);      ConfigProject.PROP_STOP_EXP = rusacis.getProperty("PropStopExp", true);      ConfigProject.PROP_TRADE_REFUSAL = rusacis.getProperty("PropTradeRefusal", true);      ConfigProject.PROP_AUTO_LOOT = rusacis.getProperty("PropAutoLoot", false);      ConfigProject.PROP_BUFF_PROTECTED = rusacis.getProperty("PropBuffProtected", false);      ConfigProject.ENABLE_ONLINE_COMMAND = rusacis.getProperty("EnabledOnlineCommand", false);      ConfigProject.MULTIPLIER_ONLINE_COMMAND = rusacis.getProperty("MultiplierOnlineCommand", 1);      ConfigProject.BOTS_PREVENTION = rusacis.getProperty("EnableBotsPrevention", false);      ConfigProject.BOTS_LOGS = rusacis.getProperty("BotsLogs", false);      ConfigProject.KILLS_COUNTER = rusacis.getProperty("KillsCounter", 60);      ConfigProject.KILLS_COUNTER_RANDOMIZATION = rusacis.getProperty("KillsCounterRandomization", 50);      ConfigProject.VALIDATION_TIME = rusacis.getProperty("ValidationTime", 60);      ConfigProject.PUNISHMENT = rusacis.getProperty("Punishment", 0);      ConfigProject.PUNISHMENT_TIME = rusacis.getProperty("PunishmentTime", 60);      ConfigProject.USE_PREMIUM_SERVICE = rusacis.getProperty("UsePremiumServices", false);      ConfigProject.ALTERNATE_DROP_LIST = rusacis.getProperty("AlternateDropList", false);      ConfigProject.ATTACK_PTS = rusacis.getProperty("AttackPTS", true);      ConfigProject.SUBCLASS_SKILLS = rusacis.getProperty("SubClassSkills", false);      ConfigProject.GAME_SUBCLASS_EVERYWHERE = rusacis.getProperty("SubclassEverywhere", false);      ConfigProject.SHOW_NPC_INFO = rusacis.getProperty("ShowNpcInfo", false);      ConfigProject.ALLOW_GRAND_BOSSES_TELEPORT = rusacis.getProperty("AllowGrandBossesTeleport", false);      ConfigProject.USE_SAY_FILTER = rusacis.getProperty("UseChatFilter", false);      ConfigProject.CHAT_FILTER_CHARS = rusacis.getProperty("ChatFilterChars", "^_^");
      try (Stream<String> lines = Files.lines(Paths.get(Config.CHAT_FILTER_FILE), StandardCharsets.UTF_8)) {
         ConfigProject.FILTER_LIST = lines.map(String::trim).filter(line -> !line.isEmpty() && line.charAt(0) != '#').toList();         Config.LOGGER.info("Loaded " + ConfigProject.FILTER_LIST.size() + " Filter Words.");
      } catch (IOException var12) {
         Config.LOGGER.warn("Error while loading chat filter words!", var12);
      }

      ConfigProject.CABAL_BUFFER = rusacis.getProperty("CabalBuffer", false);      ConfigProject.SUPER_HASTE = rusacis.getProperty("SuperHaste", false);      ConfigProject.RESTRICTED_CHAR_NAMES = rusacis.getProperty("ListOfRestrictedCharNames", "");      ConfigProject.LIST_RESTRICTED_CHAR_NAMES = new ArrayList<>();
      for (String name : ConfigProject.RESTRICTED_CHAR_NAMES.split(",")) {
         ConfigProject.LIST_RESTRICTED_CHAR_NAMES.add(name.toLowerCase());      }

      ConfigProject.FAKE_ONLINE_AMOUNT = rusacis.getProperty("FakeOnlineAmount", 1);      ConfigProject.BUFFS_CATEGORY = rusacis.getProperty("PremiumBuffsCategory", "");      ConfigProject.PREMIUM_BUFFS_CATEGORY = new ArrayList<>();
      for (String buffs : ConfigProject.BUFFS_CATEGORY.split(",")) {
         ConfigProject.PREMIUM_BUFFS_CATEGORY.add(buffs);      }

      ConfigProject.ANTIFEED_ENABLE = rusacis.getProperty("AntiFeedEnable", false);      ConfigProject.ANTIFEED_DUALBOX = rusacis.getProperty("AntiFeedDualbox", true);      ConfigProject.ANTIFEED_DISCONNECTED_AS_DUALBOX = rusacis.getProperty("AntiFeedDisconnectedAsDualbox", true);      ConfigProject.ANTIFEED_INTERVAL = rusacis.getProperty("AntiFeedInterval", 120) * 1000;      ConfigProject.DUALBOX_CHECK_MAX_PLAYERS_PER_IP = rusacis.getProperty("DualboxCheckMaxPlayersPerIP", 0);      ConfigProject.DUALBOX_CHECK_MAX_OLYMPIAD_PARTICIPANTS_PER_IP = rusacis.getProperty("DualboxCheckMaxOlympiadParticipantsPerIP", 0);      String[] autoLootItemIds = rusacis.getProperty("AutoLootItemIds", "0").split(",");
      ConfigProject.AUTO_LOOT_ITEM_IDS = new it.unimi.dsi.fastutil.ints.IntOpenHashSet(autoLootItemIds.length);
      for (String item : autoLootItemIds) {
         int itm = 0;

         try {
            itm = Integer.parseInt(item.trim());
         } catch (NumberFormatException var9) {
            Config.LOGGER.warn("Auto loot item ids: Wrong ItemId passed: " + item);
         }

         if (itm != 0) {
            ConfigProject.AUTO_LOOT_ITEM_IDS.add(itm);
         }
      }

      ConfigProject.HIT_TIME = rusacis.getProperty("HitTime", false);      ConfigProject.SHOW_RAID_HTM = rusacis.getProperty("ShowRaidHtm", false);      ConfigProject.SHOW_EPIC_HTM = rusacis.getProperty("ShowEpicHtm", false);      ConfigProject.USE_CONFIG_RAID_GRAND_BOSS_RESPAWN = rusacis.getProperty("UseConfigRaidGrandBossRespawn", true);      long[] raidRespawn = Config.parseBossRespawn(rusacis.getProperty("RaidBossRespawn", ""));
      ConfigProject.RAID_BOSS_RESPAWN_BASE_MS = raidRespawn[0];      ConfigProject.RAID_BOSS_RESPAWN_RANDOM_MS = raidRespawn[1];      long[] grandRespawn = Config.parseBossRespawn(rusacis.getProperty("GrandBossRespawn", ""));
      ConfigProject.GRAND_BOSS_RESPAWN_BASE_MS = grandRespawn[0];      ConfigProject.GRAND_BOSS_RESPAWN_RANDOM_MS = grandRespawn[1];      ConfigProject.RAID_BOSS_RESPAWN_OVERRIDES = Config.parseBossRespawnOverrides(rusacis.getProperty("RaidBossRespawnById", ""));      ConfigProject.GRAND_BOSS_RESPAWN_OVERRIDES = Config.parseBossRespawnOverrides(rusacis.getProperty("GrandBossRespawnById", ""));      ConfigProject.RAID_BOSS_TELEPORT_ENABLED = rusacis.getProperty("RaidBossTeleportEnabled", true);      ConfigProject.EPIC_BOSS_TELEPORT_ENABLED = rusacis.getProperty("EpicBossTeleportEnabled", true);      ConfigProject.BOSS_TELEPORT_MIN_RANGE = rusacis.getProperty("BossTeleportMinRange", 600);      ConfigProject.BOSS_TELEPORT_MAX_RANGE = rusacis.getProperty("BossTeleportMaxRange", 1200);      ConfigProject.BOSS_TELEPORT_ITEM_ID = rusacis.getProperty("BossTeleportItemId", 0);      ConfigProject.BOSS_TELEPORT_ITEM_COUNT = rusacis.getProperty("BossTeleportItemCount", 0L);      ConfigProject.TIME_ZONE = rusacis.getProperty("TimeZone", "GMT+2");      ConfigProject.DATE_FORMAT = rusacis.getProperty("DateFormat", "E MMM dd HH:mm yyyy 'GMT+2'");      ConfigProject.CUSTOM_BUFFER_MANAGER_NPC = rusacis.getProperty("CustomBufferManagerNpc", false);      ConfigProject.SKIP_CATEGORY = rusacis.getProperty("SkipCategory", "").split(",");      ConfigProject.BARAKIEL = rusacis.getProperty("Barakiel", false);      ConfigProject.CREATURE_SEE = rusacis.getProperty("CreatureSee", true);      ConfigProject.NEW_REGEN = rusacis.getProperty("NewRegen", false);      ConfigProject.CATACOMBS_IN_ANY_PERIOD = rusacis.getProperty("CatacombsInAnyPeriod", false);      ConfigProject.STRICT_SEVENSIGNS = rusacis.getProperty("StrictSevenSigns", true);      ConfigProject.CLASS_OVERLORD = rusacis.getProperty("ClassOverlord", false);      ConfigProject.RACE_ELF = rusacis.getProperty("RaceElf", false);      ConfigProject.RESTRICTED_CLASSES = rusacis.getProperty("RestrictedClasses", false);      ConfigProject.ENABLE_COMMAND_GOLDBAR = rusacis.getProperty("BankingEnabled", false);      ConfigProject.BANKING_SYSTEM_GOLDBARS = rusacis.getProperty("BankingGoldbarCount", 1);      ConfigProject.BANKING_SYSTEM_ADENA = rusacis.getProperty("BankingAdenaCount", 5000);      ConfigProject.AUTO_POTIONS_ENABLED = rusacis.getProperty("AutoPotionsEnabled", false);      ConfigProject.AUTO_POTIONS_IN_OLYMPIAD = rusacis.getProperty("AutoPotionsInOlympiad", false);      ConfigProject.AUTO_POTION_MIN_LEVEL = rusacis.getProperty("AutoPotionMinimumLevel", 1);      ConfigProject.ACP_PERIOD = rusacis.getProperty("AcpPeriod", 500);      ConfigProject.AUTO_CP_ENABLED = rusacis.getProperty("AutoCpEnabled", true);      ConfigProject.AUTO_HP_ENABLED = rusacis.getProperty("AutoHpEnabled", true);      ConfigProject.AUTO_MP_ENABLED = rusacis.getProperty("AutoMpEnabled", true);      ConfigProject.AUTO_CP_ITEM_IDS = new HashSet<>();
      for (String s : rusacis.getProperty("AutoCpItemIds", "0").split(",")) {
         ConfigProject.AUTO_CP_ITEM_IDS.add(Integer.parseInt(s));      }

      ConfigProject.AUTO_HP_ITEM_IDS = new HashSet<>();
      for (String s : rusacis.getProperty("AutoHpItemIds", "0").split(",")) {
         ConfigProject.AUTO_HP_ITEM_IDS.add(Integer.parseInt(s));      }

      ConfigProject.AUTO_MP_ITEM_IDS = new HashSet<>();
      for (String s : rusacis.getProperty("AutoMpItemIds", "0").split(",")) {
         ConfigProject.AUTO_MP_ITEM_IDS.add(Integer.parseInt(s));      }

      ConfigProject.MULTISELL_MAX_AMOUNT = rusacis.getProperty("MultisellMaxAmount", 9999);      ConfigProject.ENABLED_AUCTION = rusacis.getProperty("EnabledAuction", false);      ConfigProject.AUCTION_LIMIT_ITEM = rusacis.getProperty("AuctionLimitItem", 20);      ConfigProject.AUCTION_FEE = rusacis.getProperty("AuctionFee", 15000);      ConfigProject.AUCTION_ITEM_FEE = rusacis.getProperty("AuctionItemFee", 57);      ConfigProject.AUCTION_ITEM_FEE_NAME = rusacis.getProperty("AuctionItemFeeName", "Adena");      ConfigProject.AUTOFARM_ENABLED = rusacis.getProperty("AutoFarmEnabled", false);      ConfigProject.AUTOFARM_MAX_ZONE_AREA = rusacis.getProperty("MaxZoneArea", 7000000);      ConfigProject.AUTOFARM_MAX_ROUTE_PERIMITER = rusacis.getProperty("MaxRoutePerimeter", 7000000);      ConfigProject.AUTOFARM_MAX_OPEN_RADIUS = rusacis.getProperty("MaxOpenRadius", 0);      ConfigProject.AUTOFARM_MAX_ZONES = rusacis.getProperty("MaxZones", 5);      ConfigProject.AUTOFARM_MAX_ROUTES = rusacis.getProperty("MaxRoutes", 5);      ConfigProject.AUTOFARM_MAX_ZONE_NODES = rusacis.getProperty("MaxZoneNodes", 15);      ConfigProject.AUTOFARM_MAX_ROUTE_NODES = rusacis.getProperty("MaxRouteNodes", 30);      ConfigProject.AUTOFARM_MAX_TIMER = rusacis.getProperty("MaxTimer", 0);      ConfigProject.AUTOFARM_DAILY_LIMIT_HOURS = rusacis.getProperty("AutoFarmDailyLimitHours", 2);      ConfigProject.AUTOFARM_RESET_CYCLE_HOURS = rusacis.getProperty("AutoFarmResetCycleHours", 24);      ConfigProject.AUTOFARM_PREMIUM_UNLIMITED = rusacis.getProperty("AutoFarmPremiumUnlimited", true);      ConfigProject.AUTOFARM_HP_HEAL_RATE = (double)rusacis.getProperty("HpHealRate", 80) / 100.0;      ConfigProject.AUTOFARM_MP_HEAL_RATE = (double)rusacis.getProperty("MpHealRate", 80) / 100.0;      ConfigProject.AUTOFARM_DEBUFF_CHANCE = rusacis.getProperty("DebuffChance", 30);      ConfigProject.AUTOFARM_HP_POTIONS = rusacis.getProperty("HpPotions", new int[0]);      ConfigProject.AUTOFARM_MP_POTIONS = rusacis.getProperty("MpPotions", new int[0]);      ConfigProject.AUTOFARM_ALLOW_DUALBOX = rusacis.getProperty("AllowDualbox", true);      ConfigProject.AUTOFARM_DISABLE_TOWN = rusacis.getProperty("DisableTown", true);      ConfigProject.AUTOFARM_SEND_LOG_MESSAGES = rusacis.getProperty("SendLogMessages", false);      ConfigProject.AUTOFARM_CHANGE_PLAYER_TITLE = rusacis.getProperty("ChangePlayerTitle", false);      ConfigProject.AUTOFARM_CHANGE_PLAYER_NAME_COLOR = rusacis.getProperty("ChangePlayerNameColor", false);      ConfigProject.AUTOFARM_PLAYER_NAME_COLOR = rusacis.getProperty("PlayerNameColor", "000000");      ConfigProject.AUTOFARM_DEBUG_RETURN = rusacis.getProperty("DebugAutoFarmReturn", false);      ConfigProject.ENABLE_OFFLINE_FARM_COMMAND = rusacis.getProperty("EnableOfflineFarmCommand", true);      ConfigProject.OFFLINE_FARM_PREMIUM = rusacis.getProperty("OfflineFarmPremium", false);      ConfigProject.OFFLINE_FARM_LOGOUT_ON_DEATH = rusacis.getProperty("OfflineFarmLogoutOnDeath", true);      ConfigProject.DUALBOX_CHECK_MAX_OFFLINEPLAY_PER_IP = rusacis.getProperty("DualboxCheckMaxOfflinePlayPerIP", 3);      ConfigProject.DUALBOX_CHECK_MAX_OFFLINEPLAY_PREMIUM_PER_IP = rusacis.getProperty("DualboxCheckMaxOfflinePlayPremiumPerIP", 5);      ConfigProject.DUALBOX_COUNT_OFFLINE_TRADERS = rusacis.getProperty("DualboxCountOfflineTraders", true);      ConfigProject.AWAY_PLAY_SET_NAME_COLOR = rusacis.getProperty("OfflineFarmSetNameColor", true);      ConfigProject.AWAY_PLAY_NAME_COLOR = rusacis.getProperty("OfflineFarmNameColor", "00FF00");      ConfigProject.HP_POTION_ITEM_ID = rusacis.getProperty("HpPotionItemId", 1539);      ConfigProject.MP_POTION_ITEM_ID = rusacis.getProperty("MpPotionItemId", 1540);      ConfigProject.AUTOFARM_MAX_LEVEL_DIFFERENCE = rusacis.getProperty("AutofarmMaxLevelDifference", 10);      ConfigProject.IGNORE_RAID_BOSSES = rusacis.getProperty("IgnoreRaidBosses", true);      ConfigProject.IGNORE_RAID_MINIONS = rusacis.getProperty("IgnoreRaidMinions", true);      ConfigProject.IGNORE_AGATHIONS = rusacis.getProperty("IgnoreAgathions", true);      ConfigProject.IGNORE_CHESTS = rusacis.getProperty("IgnoreChests", true);      ConfigProject.SELLBUFF_ENABLED = rusacis.getProperty("SellBuffEnable", true);      ConfigProject.SELLBUFF_MP_MULTIPLER = rusacis.getProperty("MpCostMultipler", 1);      ConfigProject.SELLBUFF_PAYMENT_ID = rusacis.getProperty("PaymentID", 57);      ConfigProject.SELLBUFF_MIN_PRICE = (long)rusacis.getProperty("MinimumPrice", 1);      ConfigProject.SELLBUFF_MAX_PRICE = (long)rusacis.getProperty("MaximumPrice", 100000000);      ConfigProject.SELLBUFF_MAX_BUFFS = rusacis.getProperty("MaxBuffs", 15);      ConfigProject.CUSTOM_TIME_BUFF = rusacis.getProperty("CustomTimeBuff", false);      ConfigProject.ENTER_ANAKAZEL = rusacis.getProperty("EnterAnakazel", false);      ConfigProject.MAX_RUN_SPEED = rusacis.getProperty("MaxRunSpeed", 250);      ConfigProject.MAX_PATK = rusacis.getProperty("MaxPAtk", 999999);      ConfigProject.MAX_MATK = rusacis.getProperty("MaxMAtk", 999999);      ConfigProject.MAX_PCRIT_RATE = rusacis.getProperty("MaxPCritRate", 500);      ConfigProject.MAX_MCRIT_RATE = rusacis.getProperty("MaxMCritRate", 200);      ConfigProject.MAX_PATK_SPEED = rusacis.getProperty("MaxPAtkSpeed", 1500);      ConfigProject.MAX_MATK_SPEED = rusacis.getProperty("MaxMAtkSpeed", 1999);      ConfigProject.MAX_EVASION = rusacis.getProperty("MaxEvasion", 250);      ConfigProject.NEW_FOLLOW = rusacis.getProperty("NewFollow", false);      ConfigProject.ENABLE_MISSION = rusacis.getProperty("EnableMission", false);      ConfigProject.RANDOM_PVP_ZONE = rusacis.getProperty("RandomPvpZone", false);      ConfigProject.PTS_EMULATION_SPAWN = rusacis.getProperty("PTSEmulationSpawn", true);      ConfigProject.PTS_EMULATION_SPAWN_DURATION = rusacis.getProperty("PTSEmulationSpawnDuraion", 60);      ConfigProject.STOP_TOGGLE = rusacis.getProperty("StopToggle", true);      ConfigProject.ANNOUNCE_DIE_RAIDBOSS = rusacis.getProperty("AnnounceDieRaidBoss", false);      ConfigProject.ANNOUNCE_SPAWN_RAIDBOSS = rusacis.getProperty("AnnounceSpawnRaidBoss", false);      ConfigProject.ANNOUNCE_DIE_GRANDBOSS = rusacis.getProperty("AnnounceDieGrandBoss", false);      ConfigProject.ANNOUNCE_SPAWN_GRANDBOSS = rusacis.getProperty("AnnounceSpawnGrandBoss", false);      ConfigProject.NPC_SOULSHOT = rusacis.getProperty("NpcSoulshot", true);      ConfigProject.NPC_SPIRITSHOT = rusacis.getProperty("NpcSpiritshot", true);      ConfigProject.RETURN_HOME_MONSTER = rusacis.getProperty("ReturnHomeMonster", true);      ConfigProject.RETURN_HOME_MONSTER_RADIUS = rusacis.getProperty("ReturnHomeMonsterRadius", 2500);      ConfigProject.RETURN_HOME_RAIDBOSS = rusacis.getProperty("ReturnHomeRaidBoss", true);      ConfigProject.RETURN_HOME_RAIDBOSS_RADIUS = rusacis.getProperty("ReturnHomeRaidBossRadius", 2500);      ConfigProject.CANCEL_RETURN_ENABLED = rusacis.getProperty("CancelReturnEnabled", false);      ConfigProject.CANCEL_RETURN_MODE = rusacis.getProperty("CancelReturnMode", "CANCEL_ONLY");      ConfigProject.CANCEL_RETURN_MASS_ONLY = rusacis.getProperty("CancelReturnMassOnly", false);      ConfigProject.CANCEL_RETURN_MASS_MIN_COUNT = rusacis.getProperty("CancelReturnMassMinCount", 3);      ConfigProject.CANCEL_RETURN_TIME_MS = rusacis.getProperty("CancelReturnTimeMs", 15000);      ConfigProject.CANCEL_RETURN_SKIP_OLYMPIAD = rusacis.getProperty("CancelReturnSkipOlympiad", true);      ConfigProject.CANCEL_RETURN_NOTIFY = rusacis.getProperty("CancelReturnNotify", true);      ConfigProject.CANCEL_RETURN_MESSAGE = rusacis.getProperty("CancelReturnMessage", "Seus buffs foram restaurados.");   }
}
