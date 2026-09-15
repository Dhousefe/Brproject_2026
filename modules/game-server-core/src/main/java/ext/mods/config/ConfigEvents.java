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
 * Phase 4 config domain: ConfigEvents.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigEvents
{
   private ConfigEvents()
   {
   }

   public static boolean OLY_ENABLED;
   public static int OLY_START_TIME;
   public static int OLY_MIN;
   public static long OLY_CPERIOD;
   public static long OLY_BATTLE;
   public static long OLY_WPERIOD;
   public static long OLY_VPERIOD;
   public static int OLY_WAIT_TIME;
   public static int OLY_WAIT_BATTLE;
   public static int OLY_WAIT_END;
   public static int OLY_START_POINTS;
   public static int OLY_WEEKLY_POINTS;
   public static int OLY_MIN_MATCHES;
   public static int OLY_CLASSED;
   public static int OLY_NONCLASSED;
   public static IntIntHolder[] OLY_CLASSED_REWARD;
   public static IntIntHolder[] OLY_NONCLASSED_REWARD;
   public static int OLY_GP_PER_POINT;
   public static int OLY_HERO_POINTS;
   public static int OLY_RANK1_POINTS;
   public static int OLY_RANK2_POINTS;
   public static int OLY_RANK3_POINTS;
   public static int OLY_RANK4_POINTS;
   public static int OLY_RANK5_POINTS;
   public static int OLY_MAX_POINTS;
   public static int OLY_DIVIDER_CLASSED;
   public static int OLY_DIVIDER_NON_CLASSED;
   public static boolean OLY_ANNOUNCE_GAMES;
   public static int OLY_ENCHANT_LIMIT;
   public static boolean OLY_SHOW_MONTHLY_WINNERS;
   public static boolean SEVEN_SIGNS_BYPASS_PREREQUISITES;
   public static int FESTIVAL_MIN_PLAYER;
   public static int MAXIMUM_PLAYER_CONTRIB;
   public static int FS_PARTY_MEMBER_COUNT;
   public static int RIFT_MIN_PARTY_SIZE;
   public static int RIFT_AUTO_JUMPS_TIME_MIN;
   public static int RIFT_AUTO_JUMPS_TIME_RND;
   public static int RIFT_ENTER_COST_RECRUIT;
   public static int RIFT_ENTER_COST_SOLDIER;
   public static int RIFT_ENTER_COST_OFFICER;
   public static int RIFT_ENTER_COST_CAPTAIN;
   public static int RIFT_ENTER_COST_COMMANDER;
   public static int RIFT_ENTER_COST_HERO;
   public static int RIFT_ANAKAZEL_PORT_CHANCE;
   public static int LOTTERY_PRIZE;
   public static int LOTTERY_TICKET_PRICE;
   public static double LOTTERY_5_NUMBER_RATE;
   public static double LOTTERY_4_NUMBER_RATE;
   public static double LOTTERY_3_NUMBER_RATE;
   public static int LOTTERY_2_AND_1_NUMBER_PRIZE;
   public static boolean ALLOW_FISH_CHAMPIONSHIP;
   public static int FISH_CHAMPIONSHIP_REWARD_ITEM;
   public static int FISH_CHAMPIONSHIP_REWARD_1;
   public static int FISH_CHAMPIONSHIP_REWARD_2;
   public static int FISH_CHAMPIONSHIP_REWARD_3;
   public static int FISH_CHAMPIONSHIP_REWARD_4;
   public static int FISH_CHAMPIONSHIP_REWARD_5;
   public static int COFFER_PRICE_ID;
   public static int COFFER_PRICE_AMOUNT;
   public static boolean EVENT_COMMANDS;
   public static boolean CTF_EVENT_ENABLED;
   public static String[] CTF_EVENT_INTERVAL;
   public static int CTF_EVENT_PARTICIPATION_TIME;
   public static int CTF_EVENT_RUNNING_TIME;
   public static String CTF_NPC_LOC_NAME;
   public static int CTF_EVENT_PARTICIPATION_NPC_ID;
   public static int CTF_EVENT_TEAM_1_HEADQUARTERS_ID;
   public static int CTF_EVENT_TEAM_2_HEADQUARTERS_ID;
   public static int CTF_EVENT_TEAM_1_FLAG;
   public static int CTF_EVENT_TEAM_2_FLAG;
   public static int CTF_EVENT_CAPTURE_SKILL;
   public static int[] CTF_EVENT_PARTICIPATION_FEE = new int[2];
   public static int[] CTF_EVENT_PARTICIPATION_NPC_COORDINATES = new int[4];
   public static int CTF_EVENT_MIN_PLAYERS_IN_TEAMS;
   public static int CTF_EVENT_MAX_PLAYERS_IN_TEAMS;
   public static byte CTF_EVENT_MIN_LVL;
   public static byte CTF_EVENT_MAX_LVL;
   public static int CTF_EVENT_RESPAWN_TELEPORT_DELAY;
   public static int CTF_EVENT_START_LEAVE_TELEPORT_DELAY;
   public static String CTF_EVENT_TEAM_1_NAME;
   public static int[] CTF_EVENT_TEAM_1_COORDINATES = new int[3];
   public static int[] CTF_EVENT_TEAM_1_FLAG_COORDINATES = new int[4];
   public static String CTF_EVENT_TEAM_2_NAME;
   public static int[] CTF_EVENT_TEAM_2_COORDINATES = new int[3];
   public static int[] CTF_EVENT_TEAM_2_FLAG_COORDINATES = new int[4];
   public static IntIntHolder[] CTF_EVENT_REWARDS;
   public static boolean CTF_EVENT_TARGET_TEAM_MEMBERS_ALLOWED;
   public static boolean CTF_EVENT_SCROLL_ALLOWED;
   public static boolean CTF_EVENT_POTIONS_ALLOWED;
   public static boolean CTF_EVENT_SUMMON_BY_ITEM_ALLOWED;
   public static List<Integer> CTF_DOORS_IDS_TO_OPEN;
   public static List<Integer> CTF_DOORS_IDS_TO_CLOSE;
   public static boolean CTF_REWARD_TEAM_TIE;
   public static int CTF_EVENT_EFFECTS_REMOVAL;
   public static Map<Integer, Integer> CTF_EVENT_FIGHTER_BUFFS;
   public static Map<Integer, Integer> CTF_EVENT_MAGE_BUFFS;
   public static boolean ALLOW_CTF_DLG;
   public static int CTF_EVENT_MAX_PARTICIPANTS_PER_IP;
   public static boolean DM_EVENT_ENABLED;
   public static String[] DM_EVENT_INTERVAL;
   public static int DM_EVENT_PARTICIPATION_TIME;
   public static int DM_EVENT_RUNNING_TIME;
   public static String DM_NPC_LOC_NAME;
   public static int DM_EVENT_PARTICIPATION_NPC_ID;
   public static int[] DM_EVENT_PARTICIPATION_FEE = new int[2];
   public static int[] DM_EVENT_PARTICIPATION_NPC_COORDINATES = new int[4];
   public static int DM_EVENT_MIN_PLAYERS;
   public static int DM_EVENT_MAX_PLAYERS;
   public static byte DM_EVENT_MIN_LVL;
   public static byte DM_EVENT_MAX_LVL;
   public static List<int[]> DM_EVENT_PLAYER_COORDINATES;
   public static int DM_EVENT_RESPAWN_TELEPORT_DELAY;
   public static int DM_EVENT_START_LEAVE_TELEPORT_DELAY;
   public static boolean DM_SHOW_TOP_RANK;
   public static int DM_TOP_RANK;
   public static Map<Integer, List<int[]>> DM_EVENT_REWARDS;
   public static int DM_REWARD_FIRST_PLAYERS;
   public static boolean DM_REWARD_PLAYERS_TIE;
   public static boolean DM_EVENT_SCROLL_ALLOWED;
   public static boolean DM_EVENT_POTIONS_ALLOWED;
   public static boolean DM_EVENT_SUMMON_BY_ITEM_ALLOWED;
   public static List<Integer> DM_DOORS_IDS_TO_OPEN;
   public static List<Integer> DM_DOORS_IDS_TO_CLOSE;
   public static int DM_EVENT_EFFECTS_REMOVAL;
   public static Map<Integer, Integer> DM_EVENT_FIGHTER_BUFFS;
   public static Map<Integer, Integer> DM_EVENT_MAGE_BUFFS;
   public static String DISABLE_ID_CLASSES_STRING_DM;
   public static List<Integer> DISABLE_ID_CLASSES_DM;
   public static boolean ALLOW_DM_DLG;
   public static int DM_EVENT_MAX_PARTICIPANTS_PER_IP;
   public static boolean LM_EVENT_ENABLED;
   public static String[] LM_EVENT_INTERVAL;
   public static int LM_EVENT_PARTICIPATION_TIME;
   public static boolean LM_EVENT_HERO;
   public static int LV_EVENT_HERO_DAYS;
   public static int LM_EVENT_RUNNING_TIME;
   public static short LM_EVENT_PLAYER_CREDITS;
   public static String LM_NPC_LOC_NAME;
   public static int LM_EVENT_PARTICIPATION_NPC_ID;
   public static int[] LM_EVENT_PARTICIPATION_FEE = new int[2];
   public static int[] LM_EVENT_PARTICIPATION_NPC_COORDINATES = new int[4];
   public static int LM_EVENT_MIN_PLAYERS;
   public static int LM_EVENT_MAX_PLAYERS;
   public static byte LM_EVENT_MIN_LVL;
   public static byte LM_EVENT_MAX_LVL;
   public static List<int[]> LM_EVENT_PLAYER_COORDINATES;
   public static int LM_EVENT_RESPAWN_TELEPORT_DELAY;
   public static int LM_EVENT_START_LEAVE_TELEPORT_DELAY;
   public static IntIntHolder[] LM_EVENT_REWARDS;
   public static boolean LM_REWARD_PLAYERS_TIE;
   public static boolean LM_EVENT_SCROLL_ALLOWED;
   public static boolean LM_EVENT_POTIONS_ALLOWED;
   public static boolean LM_EVENT_SUMMON_BY_ITEM_ALLOWED;
   public static List<Integer> LM_DOORS_IDS_TO_OPEN;
   public static List<Integer> LM_DOORS_IDS_TO_CLOSE;
   public static int LM_EVENT_EFFECTS_REMOVAL;
   public static Map<Integer, Integer> LM_EVENT_FIGHTER_BUFFS;
   public static Map<Integer, Integer> LM_EVENT_MAGE_BUFFS;
   public static String DISABLE_ID_CLASSES_STRING_LM;
   public static List<Integer> DISABLE_ID_CLASSES_LM;
   public static boolean ALLOW_LM_DLG;
   public static int LM_EVENT_MAX_PARTICIPANTS_PER_IP;
   public static boolean TVT_EVENT_ENABLED;
   public static String[] TVT_EVENT_INTERVAL;
   public static int TVT_EVENT_PARTICIPATION_TIME;
   public static int TVT_EVENT_RUNNING_TIME;
   public static String TVT_NPC_LOC_NAME;
   public static int TVT_EVENT_PARTICIPATION_NPC_ID;
   public static int[] TVT_EVENT_PARTICIPATION_NPC_COORDINATES = new int[4];
   public static int[] TVT_EVENT_PARTICIPATION_FEE = new int[2];
   public static IntIntHolder[] TVT_EVENT_REWARDS;
   public static int TVT_EVENT_MIN_PLAYERS_IN_TEAMS;
   public static int TVT_EVENT_MAX_PLAYERS_IN_TEAMS;
   public static byte TVT_EVENT_MIN_LVL;
   public static byte TVT_EVENT_MAX_LVL;
   public static int TVT_EVENT_RESPAWN_TELEPORT_DELAY;
   public static int TVT_EVENT_START_LEAVE_TELEPORT_DELAY;
   public static int TVT_EVENT_EFFECTS_REMOVAL;
   public static String TVT_EVENT_TEAM_1_NAME;
   public static int[] TVT_EVENT_TEAM_1_COORDINATES = new int[3];
   public static String TVT_EVENT_TEAM_2_NAME;
   public static int[] TVT_EVENT_TEAM_2_COORDINATES = new int[3];
   public static boolean TVT_EVENT_TARGET_TEAM_MEMBERS_ALLOWED;
   public static boolean TVT_EVENT_SCROLL_ALLOWED;
   public static boolean TVT_EVENT_POTIONS_ALLOWED;
   public static boolean TVT_EVENT_SUMMON_BY_ITEM_ALLOWED;
   public static List<Integer> TVT_DOORS_IDS_TO_OPEN;
   public static List<Integer> TVT_DOORS_IDS_TO_CLOSE;
   public static boolean TVT_REWARD_TEAM_TIE;
   public static Map<Integer, Integer> TVT_EVENT_FIGHTER_BUFFS;
   public static Map<Integer, Integer> TVT_EVENT_MAGE_BUFFS;
   public static boolean TVT_REWARD_PLAYER;
   public static String TVT_EVENT_ON_KILL;
   public static String DISABLE_ID_CLASSES_STRING_TVT;
   public static List<Integer> DISABLE_ID_CLASSES_TVT;
   public static boolean ALLOW_TVT_DLG;
   public static int TVT_EVENT_MAX_PARTICIPANTS_PER_IP;

   public static void load() {
      ExProperties events = Config.initProperties(Config.EVENTS_FILE);
      ConfigEvents.OLY_ENABLED = events.getProperty("OlympiadEnabled", true);      ConfigEvents.OLY_START_TIME = events.getProperty("OlyStartTime", 18);      ConfigEvents.OLY_MIN = events.getProperty("OlyMin", 0);      ConfigEvents.OLY_CPERIOD = events.getProperty("OlyCPeriod", 21600000L);      ConfigEvents.OLY_BATTLE = events.getProperty("OlyBattle", 180000L);      ConfigEvents.OLY_WPERIOD = events.getProperty("OlyWPeriod", 604800000L);      ConfigEvents.OLY_VPERIOD = events.getProperty("OlyVPeriod", 86400000L);      ConfigEvents.OLY_WAIT_TIME = events.getProperty("OlyWaitTime", 30);      ConfigEvents.OLY_WAIT_BATTLE = events.getProperty("OlyWaitBattle", 60);      ConfigEvents.OLY_WAIT_END = events.getProperty("OlyWaitEnd", 40);      ConfigEvents.OLY_START_POINTS = events.getProperty("OlyStartPoints", 18);      ConfigEvents.OLY_WEEKLY_POINTS = events.getProperty("OlyWeeklyPoints", 3);      ConfigEvents.OLY_MIN_MATCHES = events.getProperty("OlyMinMatchesToBeClassed", 5);      ConfigEvents.OLY_CLASSED = events.getProperty("OlyClassedParticipants", 5);      ConfigEvents.OLY_NONCLASSED = events.getProperty("OlyNonClassedParticipants", 9);      ConfigEvents.OLY_CLASSED_REWARD = events.parseIntIntList("OlyClassedReward", "6651-50");      ConfigEvents.OLY_NONCLASSED_REWARD = events.parseIntIntList("OlyNonClassedReward", "6651-30");      ConfigEvents.OLY_GP_PER_POINT = events.getProperty("OlyGPPerPoint", 1000);      ConfigEvents.OLY_HERO_POINTS = events.getProperty("OlyHeroPoints", 300);      ConfigEvents.OLY_RANK1_POINTS = events.getProperty("OlyRank1Points", 100);      ConfigEvents.OLY_RANK2_POINTS = events.getProperty("OlyRank2Points", 75);      ConfigEvents.OLY_RANK3_POINTS = events.getProperty("OlyRank3Points", 55);      ConfigEvents.OLY_RANK4_POINTS = events.getProperty("OlyRank4Points", 40);      ConfigEvents.OLY_RANK5_POINTS = events.getProperty("OlyRank5Points", 30);      ConfigEvents.OLY_MAX_POINTS = events.getProperty("OlyMaxPoints", 10);      ConfigEvents.OLY_DIVIDER_CLASSED = events.getProperty("OlyDividerClassed", 3);      ConfigEvents.OLY_DIVIDER_NON_CLASSED = events.getProperty("OlyDividerNonClassed", 5);      ConfigEvents.OLY_ANNOUNCE_GAMES = events.getProperty("OlyAnnounceGames", true);      ConfigEvents.OLY_ENCHANT_LIMIT = events.getProperty("OlyMaxEnchant", -1);      ConfigEvents.OLY_SHOW_MONTHLY_WINNERS = events.getProperty("OlyShowMonthlyWinners", false);      ConfigEvents.SEVEN_SIGNS_BYPASS_PREREQUISITES = events.getProperty("SevenSignsBypassPrerequisites", false);      ConfigEvents.FESTIVAL_MIN_PLAYER = Math.clamp((long)events.getProperty("FestivalMinPlayer", 5), 2, 9);      ConfigEvents.MAXIMUM_PLAYER_CONTRIB = events.getProperty("MaxPlayerContrib", 1000000);      ConfigEvents.FS_PARTY_MEMBER_COUNT = Math.clamp((long)events.getProperty("NeededPartyMembers", 4), 2, 9);      ConfigEvents.RIFT_MIN_PARTY_SIZE = events.getProperty("RiftMinPartySize", 2);      ConfigEvents.RIFT_AUTO_JUMPS_TIME_MIN = events.getProperty("AutoJumpsDelayMin", 8);      ConfigEvents.RIFT_AUTO_JUMPS_TIME_RND = events.getProperty("AutoJumpsDelayRnd", 5);      ConfigEvents.RIFT_ENTER_COST_RECRUIT = events.getProperty("RecruitCost", 21);      ConfigEvents.RIFT_ENTER_COST_SOLDIER = events.getProperty("SoldierCost", 24);      ConfigEvents.RIFT_ENTER_COST_OFFICER = events.getProperty("OfficerCost", 27);      ConfigEvents.RIFT_ENTER_COST_CAPTAIN = events.getProperty("CaptainCost", 30);      ConfigEvents.RIFT_ENTER_COST_COMMANDER = events.getProperty("CommanderCost", 33);      ConfigEvents.RIFT_ENTER_COST_HERO = events.getProperty("HeroCost", 36);      ConfigEvents.RIFT_ANAKAZEL_PORT_CHANCE = events.getProperty("AnakazelPortChance", 15);      ConfigEvents.LOTTERY_PRIZE = events.getProperty("LotteryPrize", 50000);      ConfigEvents.LOTTERY_TICKET_PRICE = events.getProperty("LotteryTicketPrice", 2000);      ConfigEvents.LOTTERY_5_NUMBER_RATE = events.getProperty("Lottery5NumberRate", 0.6);      ConfigEvents.LOTTERY_4_NUMBER_RATE = events.getProperty("Lottery4NumberRate", 0.2);      ConfigEvents.LOTTERY_3_NUMBER_RATE = events.getProperty("Lottery3NumberRate", 0.2);      ConfigEvents.LOTTERY_2_AND_1_NUMBER_PRIZE = events.getProperty("Lottery2and1NumberPrize", 200);      ConfigEvents.ALLOW_FISH_CHAMPIONSHIP = events.getProperty("AllowFishChampionship", true);      ConfigEvents.FISH_CHAMPIONSHIP_REWARD_ITEM = events.getProperty("FishChampionshipRewardItemId", 57);      ConfigEvents.FISH_CHAMPIONSHIP_REWARD_1 = events.getProperty("FishChampionshipReward1", 800000);      ConfigEvents.FISH_CHAMPIONSHIP_REWARD_2 = events.getProperty("FishChampionshipReward2", 500000);      ConfigEvents.FISH_CHAMPIONSHIP_REWARD_3 = events.getProperty("FishChampionshipReward3", 300000);      ConfigEvents.FISH_CHAMPIONSHIP_REWARD_4 = events.getProperty("FishChampionshipReward4", 200000);      ConfigEvents.FISH_CHAMPIONSHIP_REWARD_5 = events.getProperty("FishChampionshipReward5", 100000);      ConfigEvents.COFFER_PRICE_ID = events.getProperty("CofferPriceId", 57);      ConfigEvents.COFFER_PRICE_AMOUNT = events.getProperty("CofferPriceCount", 50000);      ConfigEvents.EVENT_COMMANDS = events.getProperty("AllowEventCommands", false);      ConfigEvents.CTF_EVENT_ENABLED = events.getProperty("CTFEventEnabled", false);      ConfigEvents.CTF_EVENT_INTERVAL = events.getProperty("CTFEventInterval", "00:00,04:00,08:00,12:00,16:00,20:00").split(",");      ConfigEvents.CTF_EVENT_PARTICIPATION_TIME = events.getProperty("CTFEventParticipationTime", 3600);      ConfigEvents.CTF_EVENT_RUNNING_TIME = events.getProperty("CTFEventRunningTime", 1800);      ConfigEvents.CTF_NPC_LOC_NAME = events.getProperty("CTFNpcLocName", "Giran Town");      ConfigEvents.CTF_EVENT_PARTICIPATION_NPC_ID = events.getProperty("CTFEventParticipationNpcId", 0);      ConfigEvents.CTF_EVENT_TEAM_1_HEADQUARTERS_ID = events.getProperty("CTFEventFirstTeamHeadquartersId", 0);      ConfigEvents.CTF_EVENT_TEAM_2_HEADQUARTERS_ID = events.getProperty("CTFEventSecondTeamHeadquartersId", 0);      ConfigEvents.CTF_EVENT_TEAM_1_FLAG = events.getProperty("CTFEventFirstTeamFlag", 0);      ConfigEvents.CTF_EVENT_TEAM_2_FLAG = events.getProperty("CTFEventSecondTeamFlag", 0);      ConfigEvents.CTF_EVENT_CAPTURE_SKILL = events.getProperty("CTFEventCaptureSkillId", 0);      ConfigEvents.CTF_EVENT_PARTICIPATION_FEE = events.getProperty("CTFEventParticipationFee", new int[]{4037, 50});      ConfigEvents.CTF_EVENT_PARTICIPATION_NPC_COORDINATES = events.getProperty("CTFEventParticipationNpcCoordinates", new int[]{83425, 148585, -3406, 0});      ConfigEvents.CTF_EVENT_MIN_PLAYERS_IN_TEAMS = events.getProperty("CTFEventMinPlayersInTeams", 1);      ConfigEvents.CTF_EVENT_MAX_PLAYERS_IN_TEAMS = events.getProperty("CTFEventMaxPlayersInTeams", 20);      ConfigEvents.CTF_EVENT_MIN_LVL = Byte.parseByte(events.getProperty("CTFEventMinPlayerLevel", "1"));      ConfigEvents.CTF_EVENT_MAX_LVL = Byte.parseByte(events.getProperty("CTFEventMaxPlayerLevel", "80"));      ConfigEvents.CTF_EVENT_RESPAWN_TELEPORT_DELAY = events.getProperty("CTFEventRespawnTeleportDelay", 20);      ConfigEvents.CTF_EVENT_START_LEAVE_TELEPORT_DELAY = events.getProperty("CTFEventStartLeaveTeleportDelay", 20);      ConfigEvents.CTF_EVENT_TEAM_1_NAME = events.getProperty("CTFEventTeam1Name", "Team1");      ConfigEvents.CTF_EVENT_TEAM_1_COORDINATES = events.getProperty("CTFEventTeam1Coordinates", new int[]{148607, 46719, -3414});      ConfigEvents.CTF_EVENT_TEAM_1_FLAG_COORDINATES = events.getProperty("CTFEventTeam1FlagCoordinates", new int[]{148314, 46715, -3412, 0});      ConfigEvents.CTF_EVENT_TEAM_2_NAME = events.getProperty("CTFEventTeam2Name", "Team2");      ConfigEvents.CTF_EVENT_TEAM_2_COORDINATES = events.getProperty("CTFEventTeam2Coordinates", new int[]{150439, 46731, -3414});      ConfigEvents.CTF_EVENT_TEAM_2_FLAG_COORDINATES = events.getProperty("CTFEventTeam2FlagCoordinates", new int[]{150686, 46713, -3414, 0});      ConfigEvents.CTF_EVENT_REWARDS = events.parseIntIntList("CTFEventReward", "57-100000;4037-20");      ConfigEvents.CTF_EVENT_TARGET_TEAM_MEMBERS_ALLOWED = events.getProperty("CTFEventTargetTeamMembersAllowed", true);      ConfigEvents.CTF_EVENT_SCROLL_ALLOWED = events.getProperty("CTFEventScrollsAllowed", false);      ConfigEvents.CTF_EVENT_POTIONS_ALLOWED = events.getProperty("CTFEventPotionsAllowed", false);      ConfigEvents.CTF_EVENT_SUMMON_BY_ITEM_ALLOWED = events.getProperty("CTFEventSummonByItemAllowed", false);      String[] CTFDoorsToOpen = events.getProperty("CTFDoorsToOpen", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.CTF_DOORS_IDS_TO_OPEN = new ArrayList<>(CTFDoorsToOpen.length);
      for (String item : CTFDoorsToOpen) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var35) {
            Config.LOGGER.warn("CTFDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.CTF_DOORS_IDS_TO_OPEN.add(itm);         }
      }

      String[] CTFDoorsToClose = events.getProperty("CTFDoorsToClose", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.CTF_DOORS_IDS_TO_CLOSE = new ArrayList<>(CTFDoorsToClose.length);
      for (String item : CTFDoorsToClose) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var34) {
            Config.LOGGER.warn("CTFDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.CTF_DOORS_IDS_TO_CLOSE.add(itm);         }
      }

      ConfigEvents.CTF_REWARD_TEAM_TIE = events.getProperty("CTFRewardTeamTie", false);      ConfigEvents.CTF_EVENT_EFFECTS_REMOVAL = events.getProperty("CTFEventEffectsRemoval", 0);      ConfigEvents.CTF_EVENT_FIGHTER_BUFFS = new HashMap<>();      String[] CtfFighterBuffs = events.getProperty("CTFEventFighterBuffs", (String[])null, ";");
      if (CtfFighterBuffs != null) {
         for (String itemData : CtfFighterBuffs) {
            if (!itemData.isEmpty()) {
               String[] item = itemData.split(",");
               ConfigEvents.CTF_EVENT_FIGHTER_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.CTF_EVENT_MAGE_BUFFS = new HashMap<>();      String[] CtfMageBuffs = events.getProperty("CTFEventMageBuffs", (String[])null, ";");
      if (CtfMageBuffs != null) {
         for (String itemDatax : CtfMageBuffs) {
            if (!itemDatax.isEmpty()) {
               String[] item = itemDatax.split(",");
               ConfigEvents.CTF_EVENT_MAGE_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.ALLOW_CTF_DLG = events.getProperty("AllowDlgCTFInvite", false);      ConfigEvents.CTF_EVENT_MAX_PARTICIPANTS_PER_IP = events.getProperty("CTFEventMaxParticipantsPerIP", 0);      ConfigEvents.DM_EVENT_ENABLED = events.getProperty("DMEventEnabled", false);      ConfigEvents.DM_EVENT_INTERVAL = events.getProperty("DMEventInterval", "01:00,05:00,09:00,13:00,17:00,21:00").split(",");      ConfigEvents.DM_EVENT_PARTICIPATION_TIME = events.getProperty("DMEventParticipationTime", 3600);      ConfigEvents.DM_EVENT_RUNNING_TIME = events.getProperty("DMEventRunningTime", 1800);      ConfigEvents.DM_NPC_LOC_NAME = events.getProperty("DMNpcLocName", "Giran Town");      ConfigEvents.DM_EVENT_PARTICIPATION_NPC_ID = events.getProperty("DMEventParticipationNpcId", 0);      ConfigEvents.DM_EVENT_PARTICIPATION_FEE = events.getProperty("DMEventParticipationFee", new int[]{4037, 50});      ConfigEvents.DM_EVENT_PARTICIPATION_NPC_COORDINATES = events.getProperty("DMEventParticipationNpcCoordinates", new int[]{83425, 148585, -3406, 0});      ConfigEvents.DM_EVENT_MIN_PLAYERS = events.getProperty("DMEventMinPlayers", 1);      ConfigEvents.DM_EVENT_MAX_PLAYERS = events.getProperty("DMEventMaxPlayers", 20);      ConfigEvents.DM_EVENT_MIN_LVL = (byte)events.getProperty("DMEventMinPlayerLevel", 1);      ConfigEvents.DM_EVENT_MAX_LVL = (byte)events.getProperty("DMEventMaxPlayerLevel", 80);      ConfigEvents.DM_EVENT_PLAYER_COORDINATES = new ArrayList<>();      String[] propertySplit = events.getProperty("DMEventPlayerCoordinates", "0,0,0").split(";");

      for (String coordPlayer : propertySplit) {
         String[] coordSplit = coordPlayer.split(",");
         if (coordSplit.length != 3) {
            Config.LOGGER.warn("DMEventPlayerCoordinates \"" + coordPlayer + "\"");
         } else {
            try {
               ConfigEvents.DM_EVENT_PLAYER_COORDINATES.add(new int[]{Integer.parseInt(coordSplit[0]), Integer.parseInt(coordSplit[1]), Integer.parseInt(coordSplit[2])});            } catch (NumberFormatException var37) {
               if (!coordPlayer.isEmpty()) {
                  Config.LOGGER.warn("DMEventPlayerCoordinates \"" + coordPlayer + "\"");
               }
            }
         }
      }

      ConfigEvents.DM_EVENT_RESPAWN_TELEPORT_DELAY = events.getProperty("DMEventRespawnTeleportDelay", 20);      ConfigEvents.DM_EVENT_START_LEAVE_TELEPORT_DELAY = events.getProperty("DMEventStartLeaveTeleportDelay", 20);      ConfigEvents.DM_SHOW_TOP_RANK = events.getProperty("DMShowTopRank", false);      ConfigEvents.DM_TOP_RANK = events.getProperty("DMTopRank", 10);      ConfigEvents.DM_EVENT_REWARDS = new HashMap<>();      ConfigEvents.DM_REWARD_FIRST_PLAYERS = events.getProperty("DMRewardFirstPlayers", 3);      propertySplit = events.getProperty("DMEventReward", "57,100000;5575,5000|57,50000|57,25000").split("\\|");
      int i = 1;
      if (ConfigEvents.DM_REWARD_FIRST_PLAYERS < propertySplit.length) {
         Config.LOGGER.warn("DMRewardFirstPlayers < DMEventReward");
      } else {
         for (String pos : propertySplit) {
            List<int[]> value = new ArrayList<>();
            String[] rewardSplit = pos.split("\\;");

            for (String rewards : rewardSplit) {
               String[] reward = rewards.split("\\-");
               if (reward.length != 2) {
                  Config.LOGGER.warn("DMEventReward \"" + pos + "\"");
               } else {
                  try {
                     value.add(new int[]{Integer.parseInt(reward[0]), Integer.parseInt(reward[1])});
                  } catch (NumberFormatException var33) {
                     Config.LOGGER.warn("DMEventReward \"" + pos + "\"");
                  }
               }

               try {
                  if (value.isEmpty()) {
                     ConfigEvents.DM_EVENT_REWARDS.put(i, ConfigEvents.DM_EVENT_REWARDS.get(i - 1));                  } else {
                     ConfigEvents.DM_EVENT_REWARDS.put(i, value);                  }
               } catch (Exception var32) {
                  Config.LOGGER.warn("DMEventReward array index out of bounds (1)");
                  var32.printStackTrace();
               }

               i++;
            }
         }

         int countPosRewards = ConfigEvents.DM_EVENT_REWARDS.size();
         if (countPosRewards < ConfigEvents.DM_REWARD_FIRST_PLAYERS) {
            for (int var54 = countPosRewards + 1; var54 <= ConfigEvents.DM_REWARD_FIRST_PLAYERS; var54++) {
               try {
                  ConfigEvents.DM_EVENT_REWARDS.put(var54, ConfigEvents.DM_EVENT_REWARDS.get(var54 - 1));               } catch (Exception var31) {
                  Config.LOGGER.warn("DMEventReward array index out of bounds (2)");
                  var31.printStackTrace();
               }
            }
         }
      }

      ConfigEvents.DM_REWARD_PLAYERS_TIE = events.getProperty("DMRewardPlayersTie", false);      ConfigEvents.DM_EVENT_SCROLL_ALLOWED = events.getProperty("DMEventScrollsAllowed", false);      ConfigEvents.DM_EVENT_POTIONS_ALLOWED = events.getProperty("DMEventPotionsAllowed", false);      ConfigEvents.DM_EVENT_SUMMON_BY_ITEM_ALLOWED = events.getProperty("DMEventSummonByItemAllowed", false);      String[] DMDoorsToOpen = events.getProperty("DMDoorsToOpen", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.DM_DOORS_IDS_TO_OPEN = new ArrayList<>(DMDoorsToOpen.length);
      for (String item : DMDoorsToOpen) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var30) {
            Config.LOGGER.warn("DMDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.DM_DOORS_IDS_TO_OPEN.add(itm);         }
      }

      String[] DMDoorsToClose = events.getProperty("DMDoorsToClose", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.DM_DOORS_IDS_TO_CLOSE = new ArrayList<>(DMDoorsToClose.length);
      for (String item : DMDoorsToClose) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var29) {
            Config.LOGGER.warn("DMDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.DM_DOORS_IDS_TO_CLOSE.add(itm);         }
      }

      ConfigEvents.DM_EVENT_EFFECTS_REMOVAL = events.getProperty("DMEventEffectsRemoval", 0);      ConfigEvents.DM_EVENT_FIGHTER_BUFFS = new HashMap<>();      String[] DmFighterBuffs = events.getProperty("DMEventFighterBuffs", (String[])null, ";");
      if (DmFighterBuffs != null) {
         for (String itemDataxx : DmFighterBuffs) {
            if (!itemDataxx.isEmpty()) {
               String[] item = itemDataxx.split(",");
               ConfigEvents.DM_EVENT_FIGHTER_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.DM_EVENT_MAGE_BUFFS = new HashMap<>();      String[] DmMageBuffs = events.getProperty("DMEventMageBuffs", (String[])null, ";");
      if (DmMageBuffs != null) {
         for (String itemDataxxx : DmMageBuffs) {
            if (!itemDataxxx.isEmpty()) {
               String[] item = itemDataxxx.split(",");
               ConfigEvents.DM_EVENT_MAGE_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.DISABLE_ID_CLASSES_STRING_DM = events.getProperty("DMDisabledForClasses");      ConfigEvents.DISABLE_ID_CLASSES_DM = new ArrayList<>();
      for (String classId : ConfigEvents.DISABLE_ID_CLASSES_STRING_DM.split(",")) {
         ConfigEvents.DISABLE_ID_CLASSES_DM.add(Integer.parseInt(classId));      }

      ConfigEvents.ALLOW_DM_DLG = events.getProperty("AllowDlgDMInvite", false);      ConfigEvents.DM_EVENT_MAX_PARTICIPANTS_PER_IP = events.getProperty("DMEventMaxParticipantsPerIP", 0);      ConfigEvents.LM_EVENT_ENABLED = events.getProperty("LMEventEnabled", false);      ConfigEvents.LM_EVENT_INTERVAL = events.getProperty("LMEventInterval", "02:00,06:00,10:00,14:00,18:00,22:00").split(",");      ConfigEvents.LM_EVENT_PARTICIPATION_TIME = events.getProperty("LMEventParticipationTime", 3600);      ConfigEvents.LM_EVENT_HERO = events.getProperty("LMEventHero", false);      ConfigEvents.LV_EVENT_HERO_DAYS = events.getProperty("LMEventHeroDays", 1);      ConfigEvents.LM_EVENT_RUNNING_TIME = events.getProperty("LMEventRunningTime", 1800);      ConfigEvents.LM_EVENT_PLAYER_CREDITS = Short.parseShort(events.getProperty("LMEventPlayerCredits", "1"));      ConfigEvents.LM_NPC_LOC_NAME = events.getProperty("LMNpcLocName", "Giran Town");      ConfigEvents.LM_EVENT_PARTICIPATION_NPC_ID = events.getProperty("LMEventParticipationNpcId", 0);      ConfigEvents.LM_EVENT_PARTICIPATION_FEE = events.getProperty("LMEventParticipationFee", new int[]{4037, 50});      ConfigEvents.LM_EVENT_PARTICIPATION_NPC_COORDINATES = events.getProperty("LMEventParticipationNpcCoordinates", new int[]{83425, 148585, -3406, 0});      ConfigEvents.LM_EVENT_MIN_PLAYERS = events.getProperty("LMEventMinPlayers", 1);      ConfigEvents.LM_EVENT_MAX_PLAYERS = events.getProperty("LMEventMaxPlayers", 20);      ConfigEvents.LM_EVENT_MIN_LVL = (byte)events.getProperty("LMEventMinPlayerLevel", 1);      ConfigEvents.LM_EVENT_MAX_LVL = (byte)events.getProperty("LMEventMaxPlayerLevel", 80);      ConfigEvents.LM_EVENT_PLAYER_COORDINATES = new ArrayList<>();      String[] propertySplitLM = events.getProperty("LMEventPlayerCoordinates", "0,0,0").split(";");

      for (String coordPlayerx : propertySplitLM) {
         String[] coordSplit = coordPlayerx.split(",");
         if (coordSplit.length != 3) {
            Config.LOGGER.warn("LMEventPlayerCoordinates \"" + coordPlayerx + "\"");
         } else {
            try {
               ConfigEvents.LM_EVENT_PLAYER_COORDINATES.add(new int[]{Integer.parseInt(coordSplit[0]), Integer.parseInt(coordSplit[1]), Integer.parseInt(coordSplit[2])});            } catch (NumberFormatException var36) {
               if (!coordPlayerx.isEmpty()) {
                  Config.LOGGER.warn("LMEventPlayerCoordinates \"" + coordPlayerx + "\"");
               }
            }
         }
      }

      ConfigEvents.LM_EVENT_RESPAWN_TELEPORT_DELAY = events.getProperty("LMEventRespawnTeleportDelay", 20);      ConfigEvents.LM_EVENT_START_LEAVE_TELEPORT_DELAY = events.getProperty("LMEventStartLeaveTeleportDelay", 20);      ConfigEvents.LM_EVENT_REWARDS = events.parseIntIntList("LMEventReward", "4037-50;57-100000");      ConfigEvents.LM_REWARD_PLAYERS_TIE = events.getProperty("LMRewardPlayersTie", false);      ConfigEvents.LM_EVENT_SCROLL_ALLOWED = events.getProperty("LMEventScrollsAllowed", false);      ConfigEvents.LM_EVENT_POTIONS_ALLOWED = events.getProperty("LMEventPotionsAllowed", false);      ConfigEvents.LM_EVENT_SUMMON_BY_ITEM_ALLOWED = events.getProperty("LMEventSummonByItemAllowed", false);      String[] LMDoorsToOpen = events.getProperty("LMDoorsToOpen", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.LM_DOORS_IDS_TO_OPEN = new ArrayList<>(LMDoorsToOpen.length);
      for (String item : LMDoorsToOpen) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var28) {
            Config.LOGGER.warn("LMDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.LM_DOORS_IDS_TO_OPEN.add(itm);         }
      }

      String[] LMDoorsToClose = events.getProperty("LMDoorsToClose", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.LM_DOORS_IDS_TO_CLOSE = new ArrayList<>(LMDoorsToClose.length);
      for (String item : LMDoorsToClose) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var27) {
            Config.LOGGER.warn("LMDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.LM_DOORS_IDS_TO_CLOSE.add(itm);         }
      }

      ConfigEvents.LM_EVENT_EFFECTS_REMOVAL = events.getProperty("LMEventEffectsRemoval", 0);      ConfigEvents.LM_EVENT_FIGHTER_BUFFS = new HashMap<>();      String[] LmFighterBuffs = events.getProperty("LMEventFighterBuffs", (String[])null, ";");
      if (LmFighterBuffs != null) {
         for (String itemDataxxxx : LmFighterBuffs) {
            if (!itemDataxxxx.isEmpty()) {
               String[] item = itemDataxxxx.split(",");
               ConfigEvents.LM_EVENT_FIGHTER_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.LM_EVENT_MAGE_BUFFS = new HashMap<>();      String[] LmMageBuffs = events.getProperty("LMEventMageBuffs", (String[])null, ";");
      if (LmMageBuffs != null) {
         for (String itemDataxxxxx : LmMageBuffs) {
            if (!itemDataxxxxx.isEmpty()) {
               String[] item = itemDataxxxxx.split(",");
               ConfigEvents.LM_EVENT_MAGE_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.DISABLE_ID_CLASSES_STRING_LM = events.getProperty("LMDisabledForClasses");      ConfigEvents.DISABLE_ID_CLASSES_LM = new ArrayList<>();
      for (String classId : ConfigEvents.DISABLE_ID_CLASSES_STRING_LM.split(",")) {
         ConfigEvents.DISABLE_ID_CLASSES_LM.add(Integer.parseInt(classId));      }

      ConfigEvents.ALLOW_LM_DLG = events.getProperty("AllowDlgLMInvite", false);      ConfigEvents.LM_EVENT_MAX_PARTICIPANTS_PER_IP = events.getProperty("LMEventMaxParticipantsPerIP", 0);      ConfigEvents.TVT_EVENT_ENABLED = events.getProperty("TvTEventEnabled", false);      ConfigEvents.TVT_EVENT_INTERVAL = events.getProperty("TvTEventInterval", "03:00,07:00,11:00,15:00,19:00,23:00").split(",");      ConfigEvents.TVT_EVENT_PARTICIPATION_TIME = events.getProperty("TvTEventParticipationTime", 3600);      ConfigEvents.TVT_EVENT_RUNNING_TIME = events.getProperty("TvTEventRunningTime", 1800);      ConfigEvents.TVT_NPC_LOC_NAME = events.getProperty("TvTNpcLocName", "Giran Town");      ConfigEvents.TVT_EVENT_PARTICIPATION_NPC_ID = events.getProperty("TvTEventParticipationNpcId", 0);      ConfigEvents.TVT_EVENT_PARTICIPATION_NPC_COORDINATES = events.getProperty("TvTEventParticipationNpcCoordinates", new int[]{83425, 148585, -3406, 0});      ConfigEvents.TVT_EVENT_PARTICIPATION_FEE = events.getProperty("TvTEventParticipationFee", new int[]{4037, 50});      ConfigEvents.TVT_EVENT_REWARDS = events.parseIntIntList("TvTEventReward", "57-100000;4037-20");      ConfigEvents.TVT_EVENT_MIN_PLAYERS_IN_TEAMS = events.getProperty("TvTEventMinPlayersInTeams", 1);      ConfigEvents.TVT_EVENT_MAX_PLAYERS_IN_TEAMS = events.getProperty("TvTEventMaxPlayersInTeams", 20);      ConfigEvents.TVT_EVENT_MIN_LVL = Byte.parseByte(events.getProperty("TvTEventMinPlayerLevel", "1"));      ConfigEvents.TVT_EVENT_MAX_LVL = Byte.parseByte(events.getProperty("TvTEventMaxPlayerLevel", "80"));      ConfigEvents.TVT_EVENT_RESPAWN_TELEPORT_DELAY = events.getProperty("TvTEventRespawnTeleportDelay", 20);      ConfigEvents.TVT_EVENT_START_LEAVE_TELEPORT_DELAY = events.getProperty("TvTEventStartLeaveTeleportDelay", 20);      ConfigEvents.TVT_EVENT_EFFECTS_REMOVAL = events.getProperty("TvTEventEffectsRemoval", 0);      ConfigEvents.TVT_EVENT_TEAM_1_NAME = events.getProperty("TvTEventTeam1Name", "Team1");      ConfigEvents.TVT_EVENT_TEAM_1_COORDINATES = events.getProperty("TvTEventTeam1Coordinates", new int[]{148476, 46061, -3411});      ConfigEvents.TVT_EVENT_TEAM_2_NAME = events.getProperty("TvTEventTeam2Name", "Team2");      ConfigEvents.TVT_EVENT_TEAM_2_COORDINATES = events.getProperty("TvTEventTeam2Coordinates", new int[]{150480, 47444, -3411});      ConfigEvents.TVT_EVENT_TARGET_TEAM_MEMBERS_ALLOWED = events.getProperty("TvTEventTargetTeamMembersAllowed", true);      ConfigEvents.TVT_EVENT_SCROLL_ALLOWED = events.getProperty("TvTEventScrollsAllowed", false);      ConfigEvents.TVT_EVENT_POTIONS_ALLOWED = events.getProperty("TvTEventPotionsAllowed", false);      ConfigEvents.TVT_EVENT_SUMMON_BY_ITEM_ALLOWED = events.getProperty("TvTEventSummonByItemAllowed", false);      String[] tvTDoorsToOpen = events.getProperty("TvTDoorsToOpen", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.TVT_DOORS_IDS_TO_OPEN = new ArrayList<>(tvTDoorsToOpen.length);
      for (String item : tvTDoorsToOpen) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var26) {
            Config.LOGGER.warn("TVTDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.TVT_DOORS_IDS_TO_OPEN.add(itm);         }
      }

      String[] tvTDoorsToClose = events.getProperty("TvTDoorsToClose", "24190001;24190002;24190003;24190004").split(";");
      ConfigEvents.TVT_DOORS_IDS_TO_CLOSE = new ArrayList<>(tvTDoorsToClose.length);
      for (String item : tvTDoorsToClose) {
         Integer itm = 0;

         try {
            itm = Integer.parseInt(item);
         } catch (NumberFormatException var25) {
            Config.LOGGER.warn("TVTDoors: Wrong doorId passed: " + item);
         }

         if (itm != 0) {
            ConfigEvents.TVT_DOORS_IDS_TO_CLOSE.add(itm);         }
      }

      ConfigEvents.TVT_REWARD_TEAM_TIE = events.getProperty("TvTRewardTeamTie", false);      ConfigEvents.TVT_EVENT_EFFECTS_REMOVAL = events.getProperty("TvTEventEffectsRemoval", 0);      ConfigEvents.TVT_EVENT_FIGHTER_BUFFS = new HashMap<>();      String[] TvtFighterBuffs = events.getProperty("TvTEventFighterBuffs", (String[])null, ";");
      if (TvtFighterBuffs != null) {
         for (String itemDataxxxxxx : TvtFighterBuffs) {
            if (!itemDataxxxxxx.isEmpty()) {
               String[] item = itemDataxxxxxx.split(",");
               ConfigEvents.TVT_EVENT_FIGHTER_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.TVT_EVENT_MAGE_BUFFS = new HashMap<>();      String[] TvtMageBuffs = events.getProperty("TvTEventMageBuffs", (String[])null, ";");
      if (TvtMageBuffs != null) {
         for (String itemDataxxxxxxx : TvtMageBuffs) {
            if (!itemDataxxxxxxx.isEmpty()) {
               String[] item = itemDataxxxxxxx.split(",");
               ConfigEvents.TVT_EVENT_MAGE_BUFFS.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]));            }
         }
      }

      ConfigEvents.TVT_REWARD_PLAYER = events.getProperty("TvTRewardOnlyKillers", false);      ConfigEvents.TVT_EVENT_ON_KILL = events.getProperty("TvTEventOnKill", "pmteam");      ConfigEvents.DISABLE_ID_CLASSES_STRING_TVT = events.getProperty("TvTDisabledForClasses");      ConfigEvents.DISABLE_ID_CLASSES_TVT = new ArrayList<>();
      for (String classId : ConfigEvents.DISABLE_ID_CLASSES_STRING_TVT.split(",")) {
         ConfigEvents.DISABLE_ID_CLASSES_TVT.add(Integer.parseInt(classId));      }

      ConfigEvents.ALLOW_TVT_DLG = events.getProperty("AllowDlgTvTInvite", false);      ConfigEvents.TVT_EVENT_MAX_PARTICIPANTS_PER_IP = events.getProperty("TvTEventMaxParticipantsPerIP", 0);   }
}
