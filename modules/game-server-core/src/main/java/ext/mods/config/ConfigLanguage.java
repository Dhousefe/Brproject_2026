package ext.mods.config;

import java.util.stream.Stream;

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
 * Phase 4 config domain: ConfigLanguage.
 * Owns fields and load() for multi-dev ownership.
 */
public final class ConfigLanguage
{
   private ConfigLanguage()
   {
   }

   public static Locale DEFAULT_LOCALE = Locale.forLanguageTag("en-US");
   public static Set<Locale> LOCALES = Set.of(DEFAULT_LOCALE);
   public static Charset CHARSET = Charset.forName("utf-8");
   public static boolean COUNTRY_LOCALE_ENABLE;
   public static boolean COUNTRY_LOCALE_NOTIFY = true;
   public static boolean COUNTRY_LOCALE_AUTO_SET = true;
   public static String COUNTRY_LOCALE_API_URL = "http://ip-api.com/json/%s?fields=status,country,countryCode";
   public static int COUNTRY_LOCALE_TIMEOUT_MS = 2500;

   public static void load() {
      ExProperties language = Config.initProperties(Config.LANGUAGE_FILE);
      ConfigLanguage.DEFAULT_LOCALE = Locale.forLanguageTag(language.getProperty("defaultLocale", "en-US"));      ConfigLanguage.LOCALES = Set.copyOf(Stream.of(language.getProperty("locales", "en-US").split(",")).map(Locale::forLanguageTag).toList());      ConfigLanguage.CHARSET = Charset.forName(language.getProperty("charset", "utf-8"));      ConfigLanguage.COUNTRY_LOCALE_ENABLE = language.getProperty("CountryLocaleEnable", true);      ConfigLanguage.COUNTRY_LOCALE_NOTIFY = language.getProperty("CountryLocaleNotify", true);      ConfigLanguage.COUNTRY_LOCALE_AUTO_SET = language.getProperty("CountryLocaleAutoSet", true);      ConfigLanguage.COUNTRY_LOCALE_API_URL = language.getProperty("CountryLocaleApiUrl", ConfigLanguage.COUNTRY_LOCALE_API_URL);      ConfigLanguage.COUNTRY_LOCALE_TIMEOUT_MS = language.getProperty("CountryLocaleTimeoutMs", ConfigLanguage.COUNTRY_LOCALE_TIMEOUT_MS);      CountryLocaleManager.reloadCountryMap(language.getProperty("CountryLocaleMap", ""));
   }
}
