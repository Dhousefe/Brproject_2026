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
 * Phase 4 config domain: ConfigHexId.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigHexId
{
   private ConfigHexId()
   {
   }

   public static int SERVER_ID;
   public static byte[] HEX_ID;

   public static void load() {
      String serverId = System.getProperty("ext.mods.Config.ServerID");
      String id = System.getProperty("ext.mods.Config.HexID");
      if (serverId != null && id != null) {
         ConfigHexId.SERVER_ID = Integer.parseInt(serverId);         ConfigHexId.HEX_ID = new BigInteger(id, 16).toByteArray();      } else {
         ExProperties hexid = Config.initProperties(Config.HEXID_FILE);
         ConfigHexId.SERVER_ID = Integer.parseInt(hexid.getProperty("ServerID"));         ConfigHexId.HEX_ID = new BigInteger(hexid.getProperty("HexID"), 16).toByteArray();      }
   }
}
