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
 * Phase 4 config domain: ConfigClans.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigClans
{
   private ConfigClans()
   {
   }

   public static int CLAN_JOIN_DAYS;
   public static int CLAN_CREATE_DAYS;
   public static int MAX_NUM_OF_CLANS_IN_ALLY;
   public static int CLAN_MEMBERS_FOR_WAR;
   public static int CLAN_WAR_PENALTY_WHEN_ENDED;
   public static int CLAN_DISSOLVE_DAYS;
   public static int ALLY_JOIN_DAYS_WHEN_LEAVED;
   public static int ALLY_JOIN_DAYS_WHEN_DISMISSED;
   public static int ACCEPT_CLAN_DAYS_WHEN_DISMISSED;
   public static int CREATE_ALLY_DAYS_WHEN_DISSOLVED;
   public static boolean MEMBERS_CAN_WITHDRAW_FROM_CLANWH;
   public static int MANOR_REFRESH_TIME;
   public static int MANOR_REFRESH_MIN;
   public static int MANOR_APPROVE_TIME;
   public static int MANOR_APPROVE_MIN;
   public static int MANOR_MAINTENANCE_MIN;
   public static int MANOR_SAVE_PERIOD_RATE;
   public static long CS_TELE_FEE_RATIO;
   public static int CS_TELE1_FEE;
   public static int CS_TELE2_FEE;
   public static long CS_SUPPORT_FEE_RATIO;
   public static int CS_SUPPORT1_FEE;
   public static int CS_SUPPORT2_FEE;
   public static int CS_SUPPORT3_FEE;
   public static int CS_SUPPORT4_FEE;
   public static long CS_MPREG_FEE_RATIO;
   public static int CS_MPREG1_FEE;
   public static int CS_MPREG2_FEE;
   public static int CS_MPREG3_FEE;
   public static int CS_MPREG4_FEE;
   public static long CS_HPREG_FEE_RATIO;
   public static int CS_HPREG1_FEE;
   public static int CS_HPREG2_FEE;
   public static int CS_HPREG3_FEE;
   public static int CS_HPREG4_FEE;
   public static int CS_HPREG5_FEE;
   public static long CS_EXPREG_FEE_RATIO;
   public static int CS_EXPREG1_FEE;
   public static int CS_EXPREG2_FEE;
   public static int CS_EXPREG3_FEE;
   public static int CS_EXPREG4_FEE;

   public static boolean ENABLE_CLAN_LEADER_CHAT_TAG;
   public static boolean ENABLE_CLAN_ROYAL_GUARD_CHAT_TAG;
   public static String CLAN_LEADER_CHAT_TAG;
   public static String CLAN_ROYAL_GUARD_CHAT_TAG;
   public static boolean ENABLE_CLAN_SKILLS_PURCHASE_SITE;
   public static double CLAN_SKILLS_PURCHASE_COST_MULTIPLIER;
   public static int SITE_CLAN_REPUTATION_DONATE_ITEM_ID;
   public static int SITE_CLAN_REPUTATION_DONATE_AMOUNT_PER_USE;
   public static int SITE_CLAN_REPUTATION_DONATE_ITEM_COST;

   public static void load() {
      ExProperties clans = Config.initProperties(Config.CLANS_FILE);
      ConfigClans.CLAN_JOIN_DAYS = clans.getProperty("DaysBeforeJoinAClan", 5);      ConfigClans.CLAN_CREATE_DAYS = clans.getProperty("DaysBeforeCreateAClan", 10);      ConfigClans.MAX_NUM_OF_CLANS_IN_ALLY = clans.getProperty("MaxNumOfClansInAlly", 3);      ConfigClans.CLAN_MEMBERS_FOR_WAR = clans.getProperty("ClanMembersForWar", 15);      ConfigClans.CLAN_WAR_PENALTY_WHEN_ENDED = clans.getProperty("ClanWarPenaltyWhenEnded", 5);      ConfigClans.CLAN_DISSOLVE_DAYS = clans.getProperty("DaysToPassToDissolveAClan", 7);      ConfigClans.ALLY_JOIN_DAYS_WHEN_LEAVED = clans.getProperty("DaysBeforeJoinAllyWhenLeaved", 1);      ConfigClans.ALLY_JOIN_DAYS_WHEN_DISMISSED = clans.getProperty("DaysBeforeJoinAllyWhenDismissed", 1);      ConfigClans.ACCEPT_CLAN_DAYS_WHEN_DISMISSED = clans.getProperty("DaysBeforeAcceptNewClanWhenDismissed", 1);      ConfigClans.CREATE_ALLY_DAYS_WHEN_DISSOLVED = clans.getProperty("DaysBeforeCreateNewAllyWhenDissolved", 10);      ConfigClans.MEMBERS_CAN_WITHDRAW_FROM_CLANWH = clans.getProperty("MembersCanWithdrawFromClanWH", false);      ConfigClans.MANOR_REFRESH_TIME = clans.getProperty("ManorRefreshTime", 20);      ConfigClans.MANOR_REFRESH_MIN = clans.getProperty("ManorRefreshMin", 0);      ConfigClans.MANOR_APPROVE_TIME = clans.getProperty("ManorApproveTime", 6);      ConfigClans.MANOR_APPROVE_MIN = clans.getProperty("ManorApproveMin", 0);      ConfigClans.MANOR_MAINTENANCE_MIN = clans.getProperty("ManorMaintenanceMin", 6);      ConfigClans.MANOR_SAVE_PERIOD_RATE = clans.getProperty("ManorSavePeriodRate", 2) * 3600000;      ConfigClans.CS_TELE_FEE_RATIO = clans.getProperty("CastleTeleportFunctionFeeRatio", 604800000L);      ConfigClans.CS_TELE1_FEE = clans.getProperty("CastleTeleportFunctionFeeLvl1", 7000);      ConfigClans.CS_TELE2_FEE = clans.getProperty("CastleTeleportFunctionFeeLvl2", 14000);      ConfigClans.CS_SUPPORT_FEE_RATIO = clans.getProperty("CastleSupportFunctionFeeRatio", 86400000L);      ConfigClans.CS_SUPPORT1_FEE = clans.getProperty("CastleSupportFeeLvl1", 7000);      ConfigClans.CS_SUPPORT2_FEE = clans.getProperty("CastleSupportFeeLvl2", 21000);      ConfigClans.CS_SUPPORT3_FEE = clans.getProperty("CastleSupportFeeLvl3", 37000);      ConfigClans.CS_SUPPORT4_FEE = clans.getProperty("CastleSupportFeeLvl4", 52000);      ConfigClans.CS_MPREG_FEE_RATIO = clans.getProperty("CastleMpRegenerationFunctionFeeRatio", 86400000L);      ConfigClans.CS_MPREG1_FEE = clans.getProperty("CastleMpRegenerationFeeLvl1", 2000);      ConfigClans.CS_MPREG2_FEE = clans.getProperty("CastleMpRegenerationFeeLvl2", 6500);      ConfigClans.CS_MPREG3_FEE = clans.getProperty("CastleMpRegenerationFeeLvl3", 13750);      ConfigClans.CS_MPREG4_FEE = clans.getProperty("CastleMpRegenerationFeeLvl4", 20000);      ConfigClans.CS_HPREG_FEE_RATIO = clans.getProperty("CastleHpRegenerationFunctionFeeRatio", 86400000L);      ConfigClans.CS_HPREG1_FEE = clans.getProperty("CastleHpRegenerationFeeLvl1", 1000);      ConfigClans.CS_HPREG2_FEE = clans.getProperty("CastleHpRegenerationFeeLvl2", 1500);      ConfigClans.CS_HPREG3_FEE = clans.getProperty("CastleHpRegenerationFeeLvl3", 2250);      ConfigClans.CS_HPREG4_FEE = clans.getProperty("CastleHpRegenerationFeeLvl4", 3270);      ConfigClans.CS_HPREG5_FEE = clans.getProperty("CastleHpRegenerationFeeLvl5", 5166);      ConfigClans.CS_EXPREG_FEE_RATIO = clans.getProperty("CastleExpRegenerationFunctionFeeRatio", 86400000L);      ConfigClans.CS_EXPREG1_FEE = clans.getProperty("CastleExpRegenerationFeeLvl1", 9000);      ConfigClans.CS_EXPREG2_FEE = clans.getProperty("CastleExpRegenerationFeeLvl2", 15000);      ConfigClans.CS_EXPREG3_FEE = clans.getProperty("CastleExpRegenerationFeeLvl3", 21000);      ConfigClans.CS_EXPREG4_FEE = clans.getProperty("CastleExpRegenerationFeeLvl4", 30000);
      ConfigClans.ENABLE_CLAN_LEADER_CHAT_TAG = clans.getProperty("EnableClanLeaderChatTag", true);
      ConfigClans.ENABLE_CLAN_ROYAL_GUARD_CHAT_TAG = clans.getProperty("EnableClanRoyalGuardChatTag", true);
      ConfigClans.CLAN_LEADER_CHAT_TAG = clans.getProperty("ClanLeaderChatTag", "[Lider]");
      ConfigClans.CLAN_ROYAL_GUARD_CHAT_TAG = clans.getProperty("ClanRoyalGuardChatTag", "[Capitao]");
      ConfigClans.ENABLE_CLAN_SKILLS_PURCHASE_SITE = clans.getProperty("EnableClanSkillsPurchaseSite", true);
      ConfigClans.CLAN_SKILLS_PURCHASE_COST_MULTIPLIER = clans.getProperty("ClanSkillsPurchaseCostMultiplier", 1.0);
      ConfigClans.SITE_CLAN_REPUTATION_DONATE_ITEM_ID = clans.getProperty("SiteClanReputationDonateItemId", 4037);
      ConfigClans.SITE_CLAN_REPUTATION_DONATE_AMOUNT_PER_USE = clans.getProperty("SiteClanReputationDonateAmountPerUse", 1000);
      ConfigClans.SITE_CLAN_REPUTATION_DONATE_ITEM_COST = clans.getProperty("SiteClanReputationDonateItemCost", 1);
   }

}
