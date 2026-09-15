/*
 * Copyleft © 2024-2026 L2Brproject
 * This file is part of L2Brproject.
 *
 * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 *
 * L2Brproject is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ext.mods.gameserver.data.manager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import ext.mods.Config;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.GameClient;
import ext.mods.config.ConfigLanguage;

/**
 * Detects the player's country via IP geolocation and applies a matching
 * Locale using {@code language.properties -> CountryLocaleMap}.
 *
 * <p>Called from {@link ext.mods.gameserver.network.clientpackets.EnterWorld}
 * after the player object exists. The HTTP lookup runs on {@link ThreadPool}
 * so the login handshake is never blocked; failure paths silently fall back
 * to the default Locale.</p>
 */
public final class CountryLocaleManager
{
	private static final CLogger LOGGER = new CLogger(CountryLocaleManager.class.getName());

	/** Sysstring ids (see game/data/locale/<lang>/sysstring.xml, keys 12100..12102). */
	private static final int SYS_COUNTRY_LOCALE_NOTIFY = 12100;
	private static final int SYS_COUNTRY_LOCALE_NOT_FOUND = 12101;
	private static final int SYS_COUNTRY_LOCALE_INVALID = 12102;

	/** Tracks players already processed so re-entering world does not spam them. */
	private final Set<Integer> _processed = ConcurrentHashMap.newKeySet();

	/**
	 * IP -> (countryCode, expiresAtMs) cache. Avoids hammering ip-api.com on
	 * repeated logins from the same address; entries expire after CACHE_TTL_MS.
	 */
	private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30);
	private final ConcurrentHashMap<String, CacheEntry> _geoCache = new ConcurrentHashMap<>();

	private static final class CacheEntry
	{
		final String countryCode;
		final long expiresAtMs;

		CacheEntry(String countryCode, long expiresAtMs)
		{
			this.countryCode = countryCode;
			this.expiresAtMs = expiresAtMs;
		}

		boolean isExpired()
		{
			return System.currentTimeMillis() > expiresAtMs;
		}
	}

	protected CountryLocaleManager()
	{
	}

	public static CountryLocaleManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	/**
	 * Parses the {@code CountryLocaleMap} property and fills
	 * {@link Config#COUNTRY_LOCALE_MAP}.
	 * <p>Expected format: {@code "BR=pt-BR,US=en-US,RU=ru-RU"} — entries are
	 * trimmed, country code is upper-cased, and malformed pairs are skipped.</p>
	 * @param rawMap the value from {@code language.properties}, may be null/empty.
	 */
	public static void reloadCountryMap(String rawMap)
	{
		Config.COUNTRY_LOCALE_MAP.clear();

		if (rawMap == null || rawMap.isBlank())
			return;

		for (String entry : rawMap.split(","))
		{
			final String token = entry.trim();
			if (token.isEmpty())
				continue;

			final int eq = token.indexOf('=');
			if (eq <= 0 || eq == token.length() - 1)
			{
				LOGGER.warn("CountryLocaleManager: ignoring malformed entry '{}'.", token);
				continue;
			}

			final String country = token.substring(0, eq).trim().toUpperCase(Locale.ROOT);
			final String localeTag = token.substring(eq + 1).trim();
			if (country.isEmpty() || localeTag.isEmpty())
			{
				LOGGER.warn("CountryLocaleManager: ignoring empty entry '{}'.", token);
				continue;
			}

			Config.COUNTRY_LOCALE_MAP.put(country, localeTag);
		}

		LOGGER.info("CountryLocaleManager: loaded {} country->locale mapping(s).", Config.COUNTRY_LOCALE_MAP.size());
	}

	/**
	 * Entry point invoked from {@code EnterWorld}. Schedules an asynchronous
	 * geolocation lookup; never blocks the calling thread.
	 */
	public void onEnterWorld(Player player, GameClient client)
	{
		if (!ConfigLanguage.COUNTRY_LOCALE_ENABLE || player == null || client == null)
			return;

		final int objectId = player.getObjectId();
		if (!_processed.add(objectId))
			return;

		ThreadPool.schedule(() -> resolve(player, client), 0L);
	}

	private void resolve(Player player, GameClient client)
	{
		if (player == null || client.isDetached() || player.isOnline() == false)
			return;

		final String ip = client.getRealIpAddress();
		if (ip == null || ip.isBlank() || isPrivateIp(ip))
		{
			LOGGER.debug("CountryLocaleManager: skipping lookup for non-routable address '{}'.", ip);
			return;
		}

		final String countryCode;
		try
		{
			countryCode = lookupCountryCode(ip);
		}
		catch (Exception e)
		{
			LOGGER.warn("CountryLocaleManager: lookup failed for '{}': {}", ip, e.getMessage());
			return;
		}

		if (countryCode == null || countryCode.isBlank())
			return;

		final String localeTag = Config.COUNTRY_LOCALE_MAP.get(countryCode);
		if (localeTag == null)
		{
			if (ConfigLanguage.COUNTRY_LOCALE_NOTIFY)
				player.sendMessage(player.getSysString(SYS_COUNTRY_LOCALE_NOT_FOUND, countryCode));
			return;
		}

		// Validate the tag and only apply locales the server actually loaded.
		final Locale locale = Locale.forLanguageTag(localeTag);
		if (locale == null || locale.getLanguage().isEmpty() || !ConfigLanguage.LOCALES.contains(locale))
		{
			if (ConfigLanguage.COUNTRY_LOCALE_NOTIFY)
				player.sendMessage(player.getSysString(SYS_COUNTRY_LOCALE_INVALID, localeTag, countryCode));
			return;
		}

		if (ConfigLanguage.COUNTRY_LOCALE_AUTO_SET)
		{
			// Locale is read lazily by Player.getSysString(...); no need to push
			// a UserInfo packet (EnterWorld already broadcasted it moments ago).
			player.setLocale(locale);
		}

		if (ConfigLanguage.COUNTRY_LOCALE_NOTIFY)
			player.sendMessage(player.getSysString(SYS_COUNTRY_LOCALE_NOTIFY, countryCode, localeTag));
	}

	/**
	 * Lightweight JSON parser for the ip-api.com payload:
	 * {@code {"status":"success","country":"Brazil","countryCode":"BR"}}.
	 * Avoids pulling in a JSON dependency for two strings.
	 * <p>Result is cached per-IP for {@link #CACHE_TTL_MS} ms to avoid the
	 * ip-api.com rate limit on busy login bursts.</p>
	 */
	private String lookupCountryCode(String ip) throws Exception
	{
		// Cache hit?
		final CacheEntry cached = _geoCache.get(ip);
		if (cached != null && !cached.isExpired())
			return cached.countryCode;
		if (cached != null)
			_geoCache.remove(ip, cached); // expired, drop it

		final String urlString = String.format(ConfigLanguage.COUNTRY_LOCALE_API_URL, ip);
		final URL url = new URL(urlString);
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(ConfigLanguage.COUNTRY_LOCALE_TIMEOUT_MS);
		conn.setReadTimeout(ConfigLanguage.COUNTRY_LOCALE_TIMEOUT_MS);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
		conn.setRequestProperty("User-Agent", "L2Brproject/1.0");

		final int status;
		String countryCode;
		try
		{
			status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK)
			{
				// Don't cache failures (429/403/5xx) so the next player gets a fresh shot.
				conn.disconnect();
				if (status == HttpURLConnection.HTTP_FORBIDDEN || status == HttpURLConnection.HTTP_BAD_REQUEST || status == 429)
					LOGGER.warn("CountryLocaleManager: geolocation API returned {} (likely rate-limit); skipping cache.", status);
				return null;
			}

			final StringBuilder sb = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
					sb.append(line);
			}
			countryCode = extractJsonString(sb.toString(), "countryCode");
		}
		finally
		{
			conn.disconnect();
		}

		if (countryCode != null && !countryCode.isBlank())
			_geoCache.put(ip, new CacheEntry(countryCode, System.currentTimeMillis() + CACHE_TTL_MS));
		return countryCode;
	}

	private static String extractJsonString(String json, String key)
	{
		final String needle = "\"" + key + "\":\"";
		final int start = json.indexOf(needle);
		if (start < 0)
			return null;

		final int valueStart = start + needle.length();
		final int valueEnd = json.indexOf('"', valueStart);
		if (valueEnd < 0)
			return null;

		return json.substring(valueStart, valueEnd);
	}

	private static boolean isPrivateIp(String ip)
	{
		try
		{
			final InetAddress addr = InetAddress.getByName(ip);
			return addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress();
		}
		catch (Exception e)
		{
			return true;
		}
	}

	private static class SingletonHolder
	{
		protected static final CountryLocaleManager INSTANCE = new CountryLocaleManager();
	}
}