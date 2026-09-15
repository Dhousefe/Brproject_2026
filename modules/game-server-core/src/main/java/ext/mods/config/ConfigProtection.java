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
 * Phase 4 config domain: ConfigProtection.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigProtection
{
   private ConfigProtection()
   {
   }

   public static boolean ALLOW_GUARD_SYSTEM;
   public static int PROTECT_WINDOWS_COUNT;
   public static int GET_CLIENT_HWID;
   public static boolean ENABLE_CONSOLE_LOG;
   public static boolean PROTECT_KICK_WITH_EMPTY_HWID;
   public static boolean PROTECT_KICK_WITH_LASTERROR_HWID;
   public static byte[] GUARD_CLIENT_CRYPT_KEY;
   public static byte[] GUARD_CLIENT_CRYPT;
   public static byte[] GUARD_SERVER_CRYPT_KEY;
   public static byte[] GUARD_SERVER_CRYPT;

   // Fermata Client Fingerprint v1 (CFP)
   public static boolean FERMATA_CFP_ENABLED;
   public static boolean FERMATA_CFP_REQUIRE_PROOF;
   public static boolean FERMATA_CFP_ALLOW_CONNECTION_IDENTITY_FALLBACK;
   public static int FERMATA_CFP_CHALLENGE_TIMEOUT_SECONDS;

   public static void load() {
      ExProperties Protect = Config.initProperties(Config.PROTECTION_FILE);
      ConfigProtection.ALLOW_GUARD_SYSTEM = Protect.getProperty("AllowGuardSystem", true);
      ConfigProtection.PROTECT_WINDOWS_COUNT = Protect.getProperty("AllowedWindowsCount", 1);
      ConfigProtection.GET_CLIENT_HWID = Protect.getProperty("UseClientHWID", 2);
      ConfigProtection.ENABLE_CONSOLE_LOG = Protect.getProperty("EnableConsoleLog", false);
      ConfigProtection.PROTECT_KICK_WITH_EMPTY_HWID = Protect.getProperty("KickWithEmptyHWID", false);
      ConfigProtection.PROTECT_KICK_WITH_LASTERROR_HWID = Protect.getProperty("KickWithLastErrorHWID", false);
      
      ConfigProtection.FERMATA_CFP_ENABLED = Protect.getProperty("FermataCfpEnabled", true);
      ConfigProtection.FERMATA_CFP_REQUIRE_PROOF = Protect.getProperty("FermataCfpRequireProof", false);
      ConfigProtection.FERMATA_CFP_ALLOW_CONNECTION_IDENTITY_FALLBACK = Protect.getProperty("FermataCfpAllowConnectionFallback", true);
      ConfigProtection.FERMATA_CFP_CHALLENGE_TIMEOUT_SECONDS = Protect.getProperty("FermataCfpChallengeTimeoutSeconds", 60);

      String key_client = "GOGX2_RB(]Slnjt15~EgyqTv%[$YR]!1E~ayK?$9[R%%m4{zoMF$D?f:zvS2q&>~";
      String key_server = "b*qR43<9J1pD>Q4Uns6FsKao~VbU0H]y`A0ytTveiWn)SuSYsM?m*eblL!pwza!t";
      byte[] keyS = key_server.getBytes();
      byte[] tmpS = new byte[32];
      byte[] keyC = key_client.getBytes();
      byte[] tmpC = new byte[32];
      System.arraycopy(keyC, 0, tmpC, 0, 32);
      ConfigProtection.GUARD_CLIENT_CRYPT_KEY = FirstKey.expandKey(tmpC, 32);
      System.arraycopy(keyC, 32, tmpC, 0, 32);
      ConfigProtection.GUARD_CLIENT_CRYPT = FirstKey.expandKey(tmpC, 32);
      System.arraycopy(keyS, 0, tmpS, 0, 32);
      ConfigProtection.GUARD_SERVER_CRYPT_KEY = FirstKey.expandKey(tmpS, 32);
      System.arraycopy(keyS, 32, tmpS, 0, 32);
      ConfigProtection.GUARD_SERVER_CRYPT = FirstKey.expandKey(tmpS, 32);
   }
}
