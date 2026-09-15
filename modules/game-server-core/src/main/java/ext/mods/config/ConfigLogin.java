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
 * Phase 4 config domain: ConfigLogin.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigLogin
{
   private ConfigLogin()
   {
   }

   public static String LOGINSERVER_HOSTNAME = "*";
   public static int LOGINSERVER_PORT = 2106;
   public static int LOGIN_TRY_BEFORE_BAN;
   public static int LOGIN_BLOCK_AFTER_BAN;
   public static boolean ACCEPT_NEW_GAMESERVER;
   public static boolean SHOW_LICENCE;
   public static boolean AUTO_CREATE_ACCOUNTS;
   public static boolean FLOOD_PROTECTION;
   public static int FAST_CONNECTION_LIMIT;
   public static int NORMAL_CONNECTION_TIME;
   public static int FAST_CONNECTION_TIME;
   public static int MAX_CONNECTION_PER_IP;
   public static boolean SHOW_CONNECT;
   public static boolean PROXY;
   public static boolean ENABLE_NATIVE_PROXY = false;
   public static int LOGINSERVER_INTERNAL_PORT = 2107;
   public static boolean ENABLE_FAIL2BAN = true;

   public static boolean DISCORD_LOGIN_ENABLED = false;
   public static String DISCORD_SERVER_ID = "brproject-2026";
   public static String DISCORD_CHALLENGE_SECRET = "default_challenge_secret_brproject_2026";
   public static String DISCORD_PROOF_PUBLIC_KEY = "";
   public static int DISCORD_CHALLENGE_TIMEOUT_SECONDS = 60;

   public static int getEffectiveLoginServerPort()
   {
      return ENABLE_NATIVE_PROXY ? LOGINSERVER_INTERNAL_PORT : LOGINSERVER_PORT;
   }

   public static void load() {
      ExProperties server = Config.initProperties(Config.LOGINSERVER_FILE);
      ConfigServer.HOSTNAME = server.getProperty("Hostname", "localhost");      ConfigLogin.LOGINSERVER_HOSTNAME = server.getProperty("LoginserverHostname", "*");      ConfigLogin.LOGINSERVER_PORT = server.getProperty("LoginserverPort", 2106);      ConfigServer.GAMESERVER_LOGIN_HOSTNAME = server.getProperty("LoginHostname", "*");      ConfigServer.GAMESERVER_LOGIN_PORT = server.getProperty("LoginPort", 9014);      ConfigLogin.LOGIN_TRY_BEFORE_BAN = server.getProperty("LoginTryBeforeBan", 3);      ConfigLogin.LOGIN_BLOCK_AFTER_BAN = server.getProperty("LoginBlockAfterBan", 600);      ConfigLogin.ACCEPT_NEW_GAMESERVER = server.getProperty("AcceptNewGameServer", false);      ConfigLogin.SHOW_LICENCE = server.getProperty("ShowLicence", true);      Config.loadDatabaseProperties(server);
      ConfigLogin.AUTO_CREATE_ACCOUNTS = server.getProperty("AutoCreateAccounts", true);      ConfigLogin.FLOOD_PROTECTION = server.getProperty("EnableFloodProtection", true);      ConfigLogin.FAST_CONNECTION_LIMIT = server.getProperty("FastConnectionLimit", 15);      ConfigLogin.NORMAL_CONNECTION_TIME = server.getProperty("NormalConnectionTime", 700);      ConfigLogin.FAST_CONNECTION_TIME = server.getProperty("FastConnectionTime", 350);      ConfigLogin.MAX_CONNECTION_PER_IP = server.getProperty("MaxConnectionPerIP", 50);      ConfigLogin.SHOW_CONNECT = server.getProperty("ShowConnect", false);      ConfigLogin.PROXY = server.getProperty("Proxy", false);
      ConfigLogin.ENABLE_NATIVE_PROXY = server.getProperty("EnableNativeProxy", false);
      ConfigLogin.LOGINSERVER_INTERNAL_PORT = server.getProperty("LoginServerInternalPort", 2107);
      ConfigLogin.ENABLE_FAIL2BAN = server.getProperty("EnableFail2Ban", true);
      ConfigLogin.DISCORD_LOGIN_ENABLED = server.getProperty("DiscordLoginEnabled", false);
      ConfigLogin.DISCORD_SERVER_ID = server.getProperty("DiscordServerId", "brproject-2026");
      ConfigLogin.DISCORD_CHALLENGE_SECRET = server.getProperty("DiscordChallengeSecret", "default_challenge_secret_brproject_2026");
      ConfigLogin.DISCORD_PROOF_PUBLIC_KEY = server.getProperty("DiscordProofPublicKey", "");
      ConfigLogin.DISCORD_CHALLENGE_TIMEOUT_SECONDS = server.getProperty("DiscordChallengeTimeoutSeconds", 60);
   }
}
