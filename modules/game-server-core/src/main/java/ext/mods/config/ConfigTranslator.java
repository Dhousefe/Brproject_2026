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
 * Phase 4 config domain: ConfigTranslator.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigTranslator
{
   private ConfigTranslator()
   {
   }

   public static String DEEPL_AUTH_KEY;
   public static String DEEP_CONTEXT_STRING;
   public static List<String> DO_NOT_TRANSLATE;

   public static void load() {
      ExProperties translator = Config.initProperties(Config.TRANSLATOR_FILE);
      ConfigTranslator.DEEPL_AUTH_KEY = translator.getProperty("DeeplAuthKey", "");      ConfigTranslator.DEEP_CONTEXT_STRING = translator.getProperty("DeeplContext", "");      ConfigTranslator.DO_NOT_TRANSLATE = Arrays.asList(translator.getProperty("DoNotTranslate", new String[0]));   }
}
