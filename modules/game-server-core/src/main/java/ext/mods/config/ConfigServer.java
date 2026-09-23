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
 * Phase 4 config domain: ConfigServer.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigServer
{
   private ConfigServer()
   {
   }

   public static String HOSTNAME;
   public static String GAMESERVER_HOSTNAME;
   public static int GAMESERVER_PORT;
   public static String GAMESERVER_LOGIN_HOSTNAME;
   public static int GAMESERVER_LOGIN_PORT;
   public static String NETWORK_ENGINE;
   public static int NETTY_BOSS_THREADS;
   public static int NETTY_WORKER_THREADS;
   public static boolean ENABLE_NATIVE_PROXY = false;
   public static boolean NATIVE_PROXY_AUTO_START = false;
   public static String NATIVE_PROXY_CONFIG_FILE = "game/data/custom/mods/proxy.xml";
   public static int GAMESERVER_INTERNAL_PORT = 7778;
   public static boolean ENABLE_FAIL2BAN = true;
   public static boolean FAIL2BAN_FIREWALL = true;

   public static boolean ENABLE_NPC_INFO_PACING = true;
   public static int NPC_INFO_IMMEDIATE_BURST_LIMIT = 8;
   public static int NPC_INFO_PACED_BATCH_SIZE = 8;
   public static int NPC_INFO_PACING_INTERVAL_MS = 40;
   public static boolean ENABLE_DELETE_OBJECT_COALESCING = true;

   public static int getEffectiveGameServerPort()
   {
      return ENABLE_NATIVE_PROXY ? GAMESERVER_INTERNAL_PORT : GAMESERVER_PORT;
   }
   public static int REQUEST_ID;
   public static boolean ACCEPT_ALTERNATE_ID;
   public static boolean USE_BLOWFISH_CIPHER;
   public static String CNAME_TEMPLATE;
   public static String DONATE_CNAME_TEMPLATE;
   public static String TITLE_TEMPLATE;
   public static String PET_NAME_TEMPLATE;
   public static String CLAN_ALLY_NAME_TEMPLATE;
   public static boolean SERVER_LIST_BRACKET;
   public static boolean SERVER_LIST_CLOCK;
   public static boolean SERVER_GMONLY;
   public static int SERVER_LIST_AGE;
   public static boolean SERVER_LIST_TESTSERVER;
   public static boolean SERVER_LIST_PVPSERVER;
   public static int DELETE_DAYS;
   public static int MAXIMUM_ONLINE_USERS;
   public static boolean AUTO_LOOT;
   public static boolean AUTO_LOOT_HERBS;
   public static boolean AUTO_LOOT_RAID;
   public static boolean ALLOW_DISCARDITEM;
   public static boolean MULTIPLE_ITEM_DROP;
   public static int HERB_AUTO_DESTROY_TIME;
   public static int ITEM_AUTO_DESTROY_TIME;
   public static int EQUIPABLE_ITEM_AUTO_DESTROY_TIME;
   public static Map<Integer, Integer> SPECIAL_ITEM_DESTROY_TIME;
   public static int PLAYER_DROPPED_ITEM_MULTIPLIER;
   public static boolean ITEMS_GC_CLEANUP_ENABLED;
   public static int ITEMS_GC_CLEANUP_TIME_MS;
   public static boolean ALLOW_FREIGHT;
   public static boolean ALLOW_WAREHOUSE;
   public static boolean ALLOW_WEAR;
   public static int WEAR_DELAY;
   public static int WEAR_PRICE;
   public static boolean ALLOW_LOTTERY;
   public static boolean ALLOW_WATER;
   public static boolean ALLOW_MANOR;
   public static boolean ALLOW_BOAT;
   public static boolean ALLOW_CURSED_WEAPONS;
   public static boolean ALLOW_SHADOW_WEAPONS;
   public static boolean ENABLE_FALLING_DAMAGE;
   public static boolean NO_SPAWNS;
   public static boolean DEVELOPER;
   public static boolean PACKET_HANDLER_DEBUG;
   public static boolean DEBUG_NET;
   public static List<String> CLIENT_PACKETS;
   public static List<String> SERVER_PACKETS;
   public static boolean LOG_CHAT;
   public static boolean LOG_ITEMS;
   public static boolean DROP_ITEMS;
   public static boolean GMAUDIT;
   public static boolean ENABLE_CUSTOM_BBS;
   public static boolean ENABLE_COMMUNITY_BOARD;
   public static String BBS_DEFAULT;
   public static int ROLL_DICE_TIME;
   public static int HERO_VOICE_TIME;
   public static int SUBCLASS_TIME;
   public static int DROP_ITEM_TIME;
   public static int SERVER_BYPASS_TIME;
   public static int MULTISELL_TIME;
   public static int MANUFACTURE_TIME;
   public static int MANOR_TIME;
   public static int SENDMAIL_TIME;
   public static int CHARACTER_SELECT_TIME;
   public static int GLOBAL_CHAT_TIME;
   public static int TRADE_CHAT_TIME;
   public static int SOCIAL_TIME;
   public static int ITEM_TIME;
   public static int ACTION_TIME;
   public static int SCHEDULED_THREAD_POOL_COUNT;
   public static int INSTANT_THREAD_POOL_COUNT;
   public static boolean L2WALKER_PROTECTION;
   public static int ZONE_TOWN;
   public static boolean SERVER_NEWS;
   public static boolean ENABLE_JIT_WARMUP;
   public static int JIT_WARMUP_ITERATIONS;
   public static boolean ENABLE_NETWORK_JIT_WARMUP;
   public static int DISRUPTOR_WARMUP_CONNECTIONS;
   public static boolean WARMUP_STATUS_UPDATE;
   public static boolean WARMUP_USER_INFO;
   public static boolean WARMUP_DISRUPTOR_BROADCAST;

   public static void load() {
      ExProperties server = Config.initProperties(Config.SERVER_FILE);
      ConfigServer.HOSTNAME = server.getProperty("Hostname", "*");      ConfigServer.GAMESERVER_HOSTNAME = server.getProperty("GameserverHostname");      ConfigServer.GAMESERVER_PORT = server.getProperty("GameserverPort", 7777);      ConfigServer.GAMESERVER_LOGIN_HOSTNAME = server.getProperty("LoginHost", "127.0.0.1");      ConfigServer.GAMESERVER_LOGIN_PORT = server.getProperty("LoginPort", 9014);      ConfigServer.NETWORK_ENGINE = server.getProperty("NetworkEngine", "NETTY");      ConfigServer.NETTY_BOSS_THREADS = server.getProperty("NettyBossThreads", 1);      ConfigServer.NETTY_WORKER_THREADS = server.getProperty("NettyWorkerThreads", 0);
      ConfigServer.ENABLE_NATIVE_PROXY = server.getProperty("EnableNativeProxy", false);
      ConfigServer.NATIVE_PROXY_AUTO_START = server.getProperty("NativeProxyAutoStart", false);
      ConfigServer.NATIVE_PROXY_CONFIG_FILE = server.getProperty("NativeProxyConfigFile", "game/data/custom/mods/proxy.xml");
      ConfigServer.GAMESERVER_INTERNAL_PORT = server.getProperty("GameServerInternalPort", 7778);
      ConfigServer.ENABLE_FAIL2BAN = server.getProperty("EnableFail2Ban", true);
      ConfigServer.FAIL2BAN_FIREWALL = server.getProperty("Fail2BanFirewall", true);
      ConfigServer.ENABLE_NPC_INFO_PACING = server.getProperty("EnableNpcInfoPacing", true);
      ConfigServer.NPC_INFO_IMMEDIATE_BURST_LIMIT = server.getProperty("NpcInfoImmediateBurstLimit", 8);
      ConfigServer.NPC_INFO_PACED_BATCH_SIZE = server.getProperty("NpcInfoPacedBatchSize", 8);
      ConfigServer.NPC_INFO_PACING_INTERVAL_MS = server.getProperty("NpcInfoPacingIntervalMs", 40);
      ConfigServer.ENABLE_DELETE_OBJECT_COALESCING = server.getProperty("EnableDeleteObjectCoalescing", true);
      ConfigServer.REQUEST_ID = server.getProperty("RequestServerID", 0);      ConfigServer.ACCEPT_ALTERNATE_ID = server.getProperty("AcceptAlternateID", true);      ConfigServer.USE_BLOWFISH_CIPHER = server.getProperty("UseBlowfishCipher", true);      Config.loadDatabaseProperties(server);
      ConfigServer.CNAME_TEMPLATE = server.getProperty("CnameTemplate", ".*");      ConfigServer.DONATE_CNAME_TEMPLATE = server.getProperty("DonateCnameTemplate", ".*");      ConfigServer.TITLE_TEMPLATE = server.getProperty("TitleTemplate", ".*");      ConfigServer.PET_NAME_TEMPLATE = server.getProperty("PetNameTemplate", ".*");      ConfigServer.CLAN_ALLY_NAME_TEMPLATE = server.getProperty("ClanAllyNameTemplate", ".*");      ConfigServer.SERVER_LIST_BRACKET = server.getProperty("ServerListBrackets", false);      ConfigServer.SERVER_LIST_CLOCK = server.getProperty("ServerListClock", false);      ConfigServer.SERVER_GMONLY = server.getProperty("ServerGMOnly", false);      ConfigServer.SERVER_LIST_AGE = server.getProperty("ServerListAgeLimit", 0);      ConfigServer.SERVER_LIST_TESTSERVER = server.getProperty("TestServer", false);      ConfigServer.SERVER_LIST_PVPSERVER = server.getProperty("PvpServer", true);      ConfigServer.DELETE_DAYS = server.getProperty("DeleteCharAfterDays", 7);      ConfigServer.MAXIMUM_ONLINE_USERS = server.getProperty("MaximumOnlineUsers", 100);      ConfigServer.AUTO_LOOT = server.getProperty("AutoLoot", false);      ConfigServer.AUTO_LOOT_HERBS = server.getProperty("AutoLootHerbs", false);      ConfigServer.AUTO_LOOT_RAID = server.getProperty("AutoLootRaid", false);      ConfigServer.ALLOW_DISCARDITEM = server.getProperty("AllowDiscardItem", true);      ConfigServer.MULTIPLE_ITEM_DROP = server.getProperty("MultipleItemDrop", true);      ConfigServer.HERB_AUTO_DESTROY_TIME = server.getProperty("AutoDestroyHerbTime", 15) * 1000;      ConfigServer.ITEM_AUTO_DESTROY_TIME = server.getProperty("AutoDestroyItemTime", 600) * 1000;      ConfigServer.EQUIPABLE_ITEM_AUTO_DESTROY_TIME = server.getProperty("AutoDestroyEquipableItemTime", 0) * 1000;      ConfigServer.SPECIAL_ITEM_DESTROY_TIME = new HashMap<>();      String[] data = server.getProperty("AutoDestroySpecialItemTime", (String[])null, ",");
      if (data != null) {
         for (String itemData : data) {
            String[] item = itemData.split("-");
            ConfigServer.SPECIAL_ITEM_DESTROY_TIME.put(Integer.parseInt(item[0]), Integer.parseInt(item[1]) * 1000);         }
      }

      ConfigServer.PLAYER_DROPPED_ITEM_MULTIPLIER = server.getProperty("PlayerDroppedItemMultiplier", 1);      ExProperties items = Config.initProperties(Config.ITEMS_FILE);
      ConfigServer.ITEMS_GC_CLEANUP_ENABLED = items.getProperty("ItemsGcCleanupEnabled", true);      int itemsGcSeconds = items.getProperty("ItemsGcCleanupTime", 120);
      ConfigServer.ITEMS_GC_CLEANUP_TIME_MS = ConfigServer.ITEMS_GC_CLEANUP_ENABLED ? itemsGcSeconds * 1000 : 0;      ConfigServer.ALLOW_FREIGHT = server.getProperty("AllowFreight", true);      ConfigServer.ALLOW_WAREHOUSE = server.getProperty("AllowWarehouse", true);      ConfigServer.ALLOW_WEAR = server.getProperty("AllowWear", true);      ConfigServer.WEAR_DELAY = server.getProperty("WearDelay", 5);      ConfigServer.WEAR_PRICE = server.getProperty("WearPrice", 10);      ConfigServer.ALLOW_LOTTERY = server.getProperty("AllowLottery", true);      ConfigServer.ALLOW_WATER = server.getProperty("AllowWater", true);      ConfigServer.ALLOW_MANOR = server.getProperty("AllowManor", true);      ConfigServer.ALLOW_BOAT = server.getProperty("AllowBoat", true);      ConfigServer.ALLOW_CURSED_WEAPONS = server.getProperty("AllowCursedWeapons", true);      ConfigServer.ALLOW_SHADOW_WEAPONS = server.getProperty("AllowShadowWeapon", true);      ConfigServer.ENABLE_FALLING_DAMAGE = server.getProperty("EnableFallingDamage", true);      ConfigServer.NO_SPAWNS = server.getProperty("NoSpawns", false);      ConfigServer.DEVELOPER = server.getProperty("Developer", false);      ConfigServer.PACKET_HANDLER_DEBUG = server.getProperty("PacketHandlerDebug", false);      ConfigServer.DEBUG_NET = server.getProperty("debugnet", server.getProperty("DebugNet", false));      ConfigServer.CLIENT_PACKETS = Arrays.asList(server.getProperty("ClientPacket", "ValidatePosition").split(","));      ConfigServer.SERVER_PACKETS = Arrays.asList(         server.getProperty(
               "ServerPacket",
               "AbnormalStatusUpdate,AcquireSkillList,Attack,AutoAttackStart,AutoAttackStop,DeleteObject,ExAutoSoulShot,ExStorageMaxCount,MoveToLocation,NpcInfo,NpcSay,SkillCoolTime,SocialAction,StatusUpdate,UserInfo"
            )
            .split(",")
      );
      ConfigServer.LOG_CHAT = server.getProperty("LogChat", false);      ConfigServer.LOG_ITEMS = server.getProperty("LogItems", false);      ConfigServer.DROP_ITEMS = server.getProperty("DropItems", false);      ConfigServer.GMAUDIT = server.getProperty("GMAudit", false);      ConfigServer.ENABLE_CUSTOM_BBS = server.getProperty("EnableCustomBbs", false);      ConfigServer.ENABLE_COMMUNITY_BOARD = server.getProperty("EnableCommunityBoard", false);      ConfigServer.BBS_DEFAULT = server.getProperty("BBSDefault", "_bbshome");      ConfigServer.ROLL_DICE_TIME = server.getProperty("RollDiceTime", 4200);      ConfigServer.HERO_VOICE_TIME = server.getProperty("HeroVoiceTime", 10000);      ConfigServer.SUBCLASS_TIME = server.getProperty("SubclassTime", 2000);      ConfigServer.DROP_ITEM_TIME = server.getProperty("DropItemTime", 1000);      ConfigServer.SERVER_BYPASS_TIME = server.getProperty("ServerBypassTime", 100);      ConfigServer.MULTISELL_TIME = server.getProperty("MultisellTime", 100);      ConfigServer.MANUFACTURE_TIME = server.getProperty("ManufactureTime", 300);      ConfigServer.MANOR_TIME = server.getProperty("ManorTime", 3000);      ConfigServer.SENDMAIL_TIME = server.getProperty("SendMailTime", 10000);      ConfigServer.CHARACTER_SELECT_TIME = server.getProperty("CharacterSelectTime", 3000);      ConfigServer.GLOBAL_CHAT_TIME = server.getProperty("GlobalChatTime", 0);      ConfigServer.TRADE_CHAT_TIME = server.getProperty("TradeChatTime", 0);      ConfigServer.SOCIAL_TIME = server.getProperty("SocialTime", 2000);      ConfigServer.ITEM_TIME = server.getProperty("ItemTime", 100);      ConfigServer.ACTION_TIME = server.getProperty("ActionTime", 2000);      ConfigServer.SCHEDULED_THREAD_POOL_COUNT = server.getProperty("ScheduledThreadPoolCount", -1);      if (ConfigServer.SCHEDULED_THREAD_POOL_COUNT == -1) {
         ConfigServer.SCHEDULED_THREAD_POOL_COUNT = Runtime.getRuntime().availableProcessors() * 4;      }

      ConfigServer.INSTANT_THREAD_POOL_COUNT = server.getProperty("InstantThreadPoolCount", -1);      if (ConfigServer.INSTANT_THREAD_POOL_COUNT == -1) {
         ConfigServer.INSTANT_THREAD_POOL_COUNT = Runtime.getRuntime().availableProcessors() * 2;      }

      ConfigServer.L2WALKER_PROTECTION = server.getProperty("L2WalkerProtection", false);      ConfigServer.ZONE_TOWN = server.getProperty("ZoneTown", 0);      ConfigServer.SERVER_NEWS = server.getProperty("ShowServerNews", false);
      ConfigServer.ENABLE_JIT_WARMUP = server.getProperty("EnableJitWarmup", true);
      ConfigServer.JIT_WARMUP_ITERATIONS = server.getProperty("JitWarmupIterations", 3000);
      ConfigServer.ENABLE_NETWORK_JIT_WARMUP = server.getProperty("EnableNetworkJitWarmup", true);
      ConfigServer.DISRUPTOR_WARMUP_CONNECTIONS = server.getProperty("DisruptorWarmupConnections", 10000);
      ConfigServer.WARMUP_STATUS_UPDATE = server.getProperty("WarmupStatusUpdate", true);
      ConfigServer.WARMUP_USER_INFO = server.getProperty("WarmupUserInfo", true);
      ConfigServer.WARMUP_DISRUPTOR_BROADCAST = server.getProperty("WarmupDisruptorBroadcast", true);
   }
}
