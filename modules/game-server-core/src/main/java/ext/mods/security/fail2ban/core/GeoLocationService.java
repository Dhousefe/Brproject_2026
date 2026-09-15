/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * IP geolocation service using ip-api.com (free, no JAR dependency).
 * HTTP-based with in-memory cache (24h TTL) and offline fallback for private/special IPs.
 */
public class GeoLocationService {
	private static final Logger LOGGER = Logger.getLogger(GeoLocationService.class.getName());
	private static final String API_URL = "http://ip-api.com/json/%s?fields=status,country,countryCode,city,isp,query";
	private static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24);
	private static final int TIMEOUT_MS = 3000;
	
	private final ConcurrentHashMap<String, CachedGeo> cache;
	
	public GeoLocationService() {
		this.cache = new ConcurrentHashMap<>();
	}
	
	/**
	 * Get geolocation info for IP. Returns cached value if available.
	 * Returns fallback ("?") if offline or API fails.
	 */
	public GeoInfo lookup(String ip) {
		if (ip == null || ip.isEmpty() || "UNKNOWN".equals(ip)) {
			return GeoInfo.unknown();
		}
		
		// Check offline first (private/special IPs)
		GeoInfo offline = checkOffline(ip);
		if (offline != null) {
			return offline;
		}
		
		// Check cache
		CachedGeo cached = cache.get(ip);
		if (cached != null && !cached.isExpired()) {
			return cached.info;
		}
		
		// Call API
		try {
			GeoInfo info = callApi(ip);
			cache.put(ip, new CachedGeo(info));
			return info;
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Geo lookup failed for " + ip, e);
			return GeoInfo.unknown();
		}
	}
	
	/**
	 * Offline check for private/special IPs.
	 */
	private GeoInfo checkOffline(String ip) {
		if (ip.startsWith("127.") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
			return new GeoInfo(ip, "Local", "LO", "Localhost", "—", "—");
		}
		if (ip.startsWith("10.") || ip.startsWith("192.168.") || 
			(ip.startsWith("172.") && isPrivate172(ip))) {
			return new GeoInfo(ip, "Private", "PR", "LAN", "—", "—");
		}
		if (ip.startsWith("169.254.")) {
			return new GeoInfo(ip, "Link-Local", "LL", "APIPA", "—", "—");
		}
		// IPv6 Unique Local (fc00::/7) and Link-Local (fe80::/10)
		String lower = ip.toLowerCase(java.util.Locale.ROOT);
		if (lower.startsWith("fe80:") || lower.startsWith("fc00:") || lower.startsWith("fd00:")) {
			return new GeoInfo(ip, "Private IPv6", "P6", "LAN/Link-Local", "—", "—");
		}
		return null;
	}
	
	private boolean isPrivate172(String ip) {
		try {
			String[] parts = ip.split("\\.");
			int second = Integer.parseInt(parts[1]);
			return second >= 16 && second <= 31;
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Call ip-api.com HTTP endpoint.
	 */
	private GeoInfo callApi(String ip) throws Exception {
		String urlStr = String.format(API_URL, ip);
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setReadTimeout(TIMEOUT_MS);
		conn.setRequestMethod("GET");
		
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
			
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			
			return parseResponse(sb.toString(), ip);
		} finally {
			conn.disconnect();
		}
	}
	
	/**
	 * Parse JSON response (simple, no external libs).
	 */
	private GeoInfo parseResponse(String json, String ip) {
		// Extract fields with simple regex (avoid external JSON lib)
		String country = extract(json, "country");
		String countryCode = extract(json, "countryCode");
		String city = extract(json, "city");
		String isp = extract(json, "isp");
		String org = extract(json, "org");
		String status = extract(json, "status");
		
		if (!"success".equals(status)) {
			return GeoInfo.unknown();
		}
		
		return new GeoInfo(ip, country, countryCode, city, isp, org);
	}
	
	private String extract(String json, String key) {
		// Simple "key":"value" or "key":null extraction
		int idx = json.indexOf("\"" + key + "\":");
		if (idx < 0) return null;
		
		int start = idx + key.length() + 4; // "key":"
		if (start >= json.length()) return null;
		
		if (json.charAt(start) == 'n') return null; // null
		
		int end = json.indexOf('"', start + 1);
		if (end < 0) return null;
		
		return json.substring(start + 1, end);
	}
	
	/**
	 * Cached geo info with timestamp.
	 */
	private static class CachedGeo {
		final GeoInfo info;
		final long timestamp;
		
		CachedGeo(GeoInfo info) {
			this.info = info;
			this.timestamp = System.currentTimeMillis();
		}
		
		boolean isExpired() {
			return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
		}
	}
	
	/**
	 * Geolocation info record.
	 */
	public record GeoInfo(
		String ip,
		String country,
		String countryCode,
		String city,
		String isp,
		String org
	) {
		public static GeoInfo unknown() {
			return new GeoInfo("?", "?", "??", "?", "?", "?");
		}
		
		/**
		 * Country code or fallback text (pure clean ASCII, zero emoji bugs).
		 */
		public String getCountryTag() {
			if (countryCode == null || countryCode.isBlank() || countryCode.equals("??")) return "[--]";
			return "[" + countryCode.toUpperCase() + "]";
		}
		
		/**
		 * Short display string for table.
		 */
		public String shortDisplay() {
			return (countryCode != null && !countryCode.isBlank() && !countryCode.equals("??"))
				? "[" + countryCode.toUpperCase() + "]"
				: "[--]";
		}
	}
}