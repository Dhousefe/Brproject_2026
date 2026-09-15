package ext.mods;

import ext.mods.commons.config.ExProperties;
import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.manager.CountryLocaleManager;
import ext.mods.gameserver.enums.GeoType;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.olympiad.enums.OlympiadPeriod;
import ext.mods.protection.hwid.crypt.FirstKey;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Config facade (Phase 4).
 * Domain fields live in {@code ext.mods.config.Config*} classes.
 * This class keeps paths, helpers, nested types, and load orchestration.
 */
public final class Config {
   public static final CLogger LOGGER = new CLogger(ext.mods.Config.class.getName());
   public static final boolean DEV_MODE = System.getProperty("ext.mods.Config.devMode", "false").equalsIgnoreCase("true");
   public static final Path BASE_PATH = Path.of(System.getProperty("ext.mods.Config.basePath", "."));
   public static final Path DATA_PATH = Path.of(System.getProperty("ext.mods.Config.dataPath", "data"));
   public static final Path CONFIG_PATH = Path.of(System.getProperty("ext.mods.Config.configPath", "config"));
   public static final String CHAT_FILTER_FILE = CONFIG_PATH.resolve("chatfilter.txt").toString();
   public static final String BOSS_HEAL_FILE = CONFIG_PATH.resolve("bossHeal.properties").toString();
   public static final String CLANS_FILE = CONFIG_PATH.resolve("clans.properties").toString();
   public static final String EVENTS_FILE = CONFIG_PATH.resolve("events.properties").toString();
   public static final String GEOENGINE_FILE = CONFIG_PATH.resolve("geoengine.properties").toString();
   public static final String HEXID_FILE = CONFIG_PATH.resolve("hexid.txt").toString();
   public static final String LANGUAGE_FILE = CONFIG_PATH.resolve("language.properties").toString();
   public static final String LOGINSERVER_FILE = CONFIG_PATH.resolve("loginserver.properties").toString();
   public static final String NPCS_FILE = CONFIG_PATH.resolve("npcs.properties").toString();
   public static final String OFFLINE_FILE = CONFIG_PATH.resolve("offlineshop.properties").toString();
   public static final String PLAYERS_FILE = CONFIG_PATH.resolve("players.properties").toString();
   public static final String RATES_FILE = CONFIG_PATH.resolve("rates.properties").toString();
   public static final String BR_FILE = CONFIG_PATH.resolve("project.properties").toString();
   public static final String SERVER_FILE = CONFIG_PATH.resolve("server.properties").toString();
   public static final String SAFE_DISCONNECT_FILE = CONFIG_PATH.resolve("safedisconnect.properties").toString();
   public static final String BOSS_ZERG_FILE = CONFIG_PATH.resolve("bosszerg.properties").toString();
   public static final String SIEGE_FILE = CONFIG_PATH.resolve("siege.properties").toString();
   public static final String PROTECTION_FILE = CONFIG_PATH.resolve("protection.properties").toString();
   public static final String TRANSLATOR_FILE = CONFIG_PATH.resolve("translator.properties").toString();
   public static final String ITEMS_FILE = CONFIG_PATH.resolve("items.properties").toString();
   public static byte FST_KEY = 110;
   public static byte SCN_KEY = 36;
   public static byte ANP_KEY = -5;
   public static byte ULT_KEY = 12;
   public static int NPROTECT_KEY = -1;
   public static final String BOSS_JEWEL_UPGRADES_FILE = CONFIG_PATH.resolve("BossJewelUpgrades.properties").toString();
   public static final Map<String, String> COUNTRY_LOCALE_MAP = new HashMap<>();
   public static String DATABASE_URL;
   public static String DATABASE_LOGIN;
   public static String DATABASE_PASSWORD;
   public static boolean TRANSLATOR_ENABLE;
   public static boolean RESERVE_HOST_ON_LOGIN = false;
   public static int MMO_SELECTOR_SLEEP_TIME = 20;
   public static int MMO_MAX_SEND_PER_PASS = 80;
   public static int MMO_MAX_READ_PER_PASS = 80;
   public static int MMO_HELPER_BUFFER_COUNT = 20;
   public static int CLIENT_PACKET_QUEUE_SIZE = MMO_MAX_READ_PER_PASS + 2;
   public static int CLIENT_PACKET_QUEUE_MAX_BURST_SIZE = MMO_MAX_READ_PER_PASS + 1;
   public static int CLIENT_PACKET_QUEUE_MAX_PACKETS_PER_SECOND = 320;
   public static int CLIENT_PACKET_QUEUE_MEASURE_INTERVAL = 5;
   public static int CLIENT_PACKET_QUEUE_MAX_AVERAGE_PACKETS_PER_SECOND = 160;
   public static int CLIENT_PACKET_QUEUE_MAX_FLOODS_PER_MIN = 2;
   public static int CLIENT_PACKET_QUEUE_MAX_OVERFLOWS_PER_MIN = 1;
   public static int CLIENT_PACKET_QUEUE_MAX_UNDERFLOWS_PER_MIN = 1;
   public static int CLIENT_PACKET_QUEUE_MAX_UNKNOWN_PER_MIN = 5;
   public static final Pattern BOSS_RESPAWN_PATTERN = Pattern.compile("^(\\d+)d\\+(\\d+)h(?:\\+(\\d+)h)?$", 2);

   private Config() {
      throw new IllegalStateException("Utility class");
   }

   public static final ExProperties initProperties(String filename) {
      ExProperties result = new ExProperties();

      try {
         result.load(new File(filename));
      } catch (Exception var3) {
         LOGGER.error("An error occured loading '{}' config.", var3, new Object[]{filename});
      }

      return result;
   }

   private static final void loadOfflineShop() {
      ext.mods.config.ConfigOfflineShop.load();
   }

   private static final void loadSafeDisconnect() {
      ext.mods.config.ConfigSafeDisconnect.load();
   }

   private static final void loadBossZerg() {
      ext.mods.config.ConfigBossZerg.load();
   }

   private static final void loadClans() {
      ext.mods.config.ConfigClans.load();
   }

   private static final void loadBossHealConfigs() {
      ext.mods.config.ConfigBossHeal.load();
   }

   private static final void loadEvents() {
      ext.mods.config.ConfigEvents.load();
   }

   private static final void loadGeoengine() {
      ext.mods.config.ConfigGeoengine.load();
   }

   private static final void loadHexID() {
      ext.mods.config.ConfigHexId.load();
   }

   private static final void loadLanguage() {
      ext.mods.config.ConfigLanguage.load();
   }

   public static final void saveHexid(int serverId, String hexId) {
      saveHexid(serverId, hexId, HEXID_FILE);
   }

   public static final void saveHexid(int serverId, String hexId, String filename) {
      try {
         File file = new File(filename);
         file.createNewFile();
         Properties hexSetting = new Properties();
         hexSetting.setProperty("ServerID", String.valueOf(serverId));
         hexSetting.setProperty("HexID", hexId);

         try (OutputStream out = new FileOutputStream(file)) {
            hexSetting.store(out, "the hexID to auth into login");
         }
      } catch (Exception var10) {
         LOGGER.error("Failed to save hex ID to '{}' file.", var10, new Object[]{filename});
      }
   }

   private static final void loadNpcs() {
      ext.mods.config.ConfigNpcs.load();
   }

   private static final void loadPlayers() {
      ext.mods.config.ConfigPlayers.load();
   }

   private static final void loadSieges() {
      ext.mods.config.ConfigSiege.load();
   }

   private static final void loadProtection() {
      ext.mods.config.ConfigProtection.load();
   }

   private static final void loadServer() {
      ext.mods.config.ConfigServer.load();
   }

   private static final void loadRates() {
      ext.mods.config.ConfigRates.load();
   }

   private static final void loadRusAcis() {
      ext.mods.config.ConfigProject.load();
   }

   public static final void loadDatabaseProperties(ExProperties properties) {
      // Default to SQLite (zero-config, no external server needed). If a user already has
      // sql.url configured to MariaDB/MySQL/Postgres/etc. in their properties, that value wins
      // and is used as-is (ConnectionPool detects the driver from the URL prefix).
      DATABASE_URL = getString(properties, "sql.url", "jdbc:sqlite:data/brproject.db");
      DATABASE_LOGIN = getString(properties, "sql.login", "");
      DATABASE_PASSWORD = getString(properties, "sql.password", "");
   }

   private static final void loadLogin() {
      ext.mods.config.ConfigLogin.load();
   }

   private static final void loadTranslator() {
      ext.mods.config.ConfigTranslator.load();
   }

   private static final void loadBossJewelUpgrades() {
      ext.mods.config.ConfigBossJewels.load();
   }

   private static final void loadDonation() {
      ext.mods.config.ConfigDonation.load();
   }

   private static final void loadVoteL2JBrasil() {
      ext.mods.config.ConfigVoteL2JBrasil.load();
   }

   public static final void loadGameServer() {
      LOGGER.info("Loading gameserver configuration files.");
      loadOfflineShop();
      loadSafeDisconnect();
      loadBossZerg();
      loadClans();
      loadEvents();
      loadGeoengine();
      loadHexID();
      loadLanguage();
      loadProtection();
      loadNpcs();
      loadPlayers();
      loadSieges();
      loadServer();
      loadRates();
      loadRusAcis();
      loadDonation();
      loadVoteL2JBrasil();
      loadTranslator();
      loadBossJewelUpgrades();
      loadBossHealConfigs();
   }

   public static final void loadLoginServer() {
      LOGGER.info("Loading loginserver configuration files.");
      loadLogin();
   }

   public static final void loadAccountManager() {
      LOGGER.info("Loading account manager configuration files.");
      loadLogin();
   }

   public static final void loadGameServerRegistration() {
      LOGGER.info("Loading gameserver registration configuration files.");
      loadLogin();
   }

   public static String getString(ExProperties properties, String name, String defaultValue) {
      String systemValue = System.getProperty(name);
      if (systemValue != null) {
         return systemValue;
      } else {
         String value = properties.getProperty(name, defaultValue);
         return value == null ? defaultValue : value;
      }
   }

   public static long[] parseBossRespawn(String value) {
      if (value != null && !value.isBlank()) {
         Matcher matcher = BOSS_RESPAWN_PATTERN.matcher(value.replace(" ", ""));
         if (!matcher.matches()) {
            return new long[]{0L, 0L};
         } else {
            long days = Long.parseLong(matcher.group(1));
            long hours = Long.parseLong(matcher.group(2));
            String randomGroup = matcher.group(3);
            long randomHours = randomGroup != null ? Long.parseLong(randomGroup) : 0L;
            long baseMs = (days * 24L + hours) * 3600000L;
            long randomMs = randomHours * 3600000L;
            return new long[]{baseMs, randomMs};
         }
      } else {
         return new long[]{0L, 0L};
      }
   }

   public static Map<Integer, long[]> parseBossRespawnOverrides(String value) {
      if (value != null && !value.isBlank()) {
         Map<Integer, long[]> overrides = new HashMap<>();
         String[] entries = value.split(";");

         for (String entry : entries) {
            if (entry != null && !entry.isBlank()) {
               String[] parts = entry.split("-", 2);
               if (parts.length == 2) {
                  try {
                     int npcId = Integer.parseInt(parts[0].trim());
                     long[] respawn = parseBossRespawn(parts[1].trim());
                     if (respawn[0] > 0L) {
                        overrides.put(npcId, respawn);
                     }
                  } catch (NumberFormatException var10) {
                  }
               }
            }
         }

         return overrides;
      } else {
         return Map.of();
      }
   }

   
   public static int[] parseDonationIntArray(String value) {
      if (value == null || value.isBlank()) {
         return new int[0];
      }
      String[] parts = value.split(",");
      int[] result = new int[parts.length];
      for (int i = 0; i < parts.length; i++) {
         try {
            result[i] = Integer.parseInt(parts[i].trim());
         } catch (NumberFormatException e) {
            result[i] = 0;
         }
      }
      return result;
   }

   public static String[] parseDonationStringArray(String value) {
      if (value == null || value.isBlank()) {
         return new String[] { "" };
      }
      return value.split(",");
   }

   public static final class ClassMasterSettings {
      private final Map<Integer, Boolean> _allowedClassChange = HashMap.newHashMap(3);
      private final Map<Integer, List<IntIntHolder>> _claimItems = HashMap.newHashMap(3);
      private final Map<Integer, List<IntIntHolder>> _rewardItems = HashMap.newHashMap(3);

      public ClassMasterSettings(String configLine) {
         if (configLine != null) {
            this.parseConfigLine(configLine.trim());
         }
      }

      private void parseConfigLine(String configLine) {
         StringTokenizer st = new StringTokenizer(configLine, ";");

         while (st.hasMoreTokens()) {
            int job = Integer.parseInt(st.nextToken());
            this._allowedClassChange.put(job, true);
            List<IntIntHolder> items = new ArrayList<>();
            if (st.hasMoreTokens()) {
               StringTokenizer st2 = new StringTokenizer(st.nextToken(), "[],");

               while (st2.hasMoreTokens()) {
                  StringTokenizer st3 = new StringTokenizer(st2.nextToken(), "()");
                  items.add(new IntIntHolder(Integer.parseInt(st3.nextToken()), Integer.parseInt(st3.nextToken())));
               }
            }

            this._claimItems.put(job, items);
            items = new ArrayList<>();
            if (st.hasMoreTokens()) {
               StringTokenizer st2 = new StringTokenizer(st.nextToken(), "[],");

               while (st2.hasMoreTokens()) {
                  StringTokenizer st3 = new StringTokenizer(st2.nextToken(), "()");
                  items.add(new IntIntHolder(Integer.parseInt(st3.nextToken()), Integer.parseInt(st3.nextToken())));
               }
            }

            this._rewardItems.put(job, items);
         }
      }

      public boolean isAllowed(int job) {
         if (this._allowedClassChange == null) {
            return false;
         } else {
            return this._allowedClassChange.containsKey(job) ? this._allowedClassChange.get(job) : false;
         }
      }

      public List<IntIntHolder> getRewardItems(int job) {
         return this._rewardItems.get(job);
      }

      public List<IntIntHolder> getRequiredItems(int job) {
         return this._claimItems.get(job);
      }
   }

   public static class JewelUpgrade {
      public final int itemId;
      public final int enchantLevel;
      public final int newItemId;

      public JewelUpgrade(int itemId, int enchantLevel, int newItemId) {
         this.itemId = itemId;
         this.enchantLevel = enchantLevel;
         this.newItemId = newItemId;
      }
   }
}
