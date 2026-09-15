/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
*/
package ext.mods.security.fail2ban.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import ext.mods.config.ConfigServer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Global Fail2Ban configuration loaded from game/data/custom/mods/proxy.xml.
 * Parses the <fail2ban> element and provides safe defaults if not present or disabled.
 */
public final class Fail2BanConfig {
	private static final Logger LOGGER = Logger.getLogger(Fail2BanConfig.class.getName());
	private static final String CONFIG_FILE = "game/data/custom/mods/proxy.xml";
	
	private final boolean enabled;
	private final boolean firewallEnabled;
	private final boolean persistenceEnabled;
	private final String persistencePath;
	private final int maxActiveBans;
	private final long gcIntervalMs;
	private final String[] ignoreIps;
	private final Map<String, Jail> jails;
	
	public Fail2BanConfig() {
		try {
			ConfigData data = loadConfig();
			this.enabled = ConfigServer.ENABLE_FAIL2BAN && data.enabled;
			this.firewallEnabled = ConfigServer.FAIL2BAN_FIREWALL && data.firewallEnabled;
			this.persistenceEnabled = data.persistenceEnabled;
			this.persistencePath = data.persistencePath;
			this.maxActiveBans = data.maxActiveBans;
			this.gcIntervalMs = data.gcIntervalMs;
			this.ignoreIps = data.ignoreIps;
			this.jails = data.jails;
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to load Fail2Ban config", e);
			throw new RuntimeException("Failed to initialize Fail2BanConfig", e);
		}
		logConfiguration();
	}
	
	private static class ConfigData {
		boolean enabled;
		boolean firewallEnabled;
		boolean persistenceEnabled;
		String persistencePath;
		int maxActiveBans;
		long gcIntervalMs;
		String[] ignoreIps;
		Map<String, Jail> jails;
	}
	
	private ConfigData loadConfig() throws Exception {
		ConfigData data = new ConfigData();
		Element cfgElement = loadConfigElement();
		
		if (cfgElement != null) {
			data.enabled = Boolean.parseBoolean(cfgElement.getAttribute("enabled"));
			data.firewallEnabled = Boolean.parseBoolean(cfgElement.getAttribute("firewallEnabled"));
			data.persistenceEnabled = Boolean.parseBoolean(cfgElement.getAttribute("persistenceEnabled"));
			data.persistencePath = cfgElement.getAttribute("persistencePath");
			data.maxActiveBans = Integer.parseInt(cfgElement.getAttribute("maxActiveBans"));
			data.gcIntervalMs = Long.parseLong(cfgElement.getAttribute("gcIntervalMs"));
			data.ignoreIps = loadIgnoreIps(cfgElement);
			data.jails = loadJails(cfgElement);
		} else {
			// Defaults
			data.enabled = true;
			data.firewallEnabled = true;
			data.persistenceEnabled = true;
			data.persistencePath = "data/fail2ban.sqlite";
			data.maxActiveBans = 50000;
			data.gcIntervalMs = 60000;
			data.ignoreIps = new String[] { "127.0.0.1", "0:0:0:0:0:0:0:1" };
			data.jails = createDefaultJails();
		}
		
		return data;
	}
	
	private Element loadConfigElement() throws Exception {
		String customPath = ConfigServer.NATIVE_PROXY_CONFIG_FILE;
		Path cfgPath = (customPath != null && !customPath.isBlank()) ? Paths.get(customPath) : Paths.get(CONFIG_FILE);
		if (!Files.exists(cfgPath)) {
			cfgPath = Paths.get("data/custom/mods/proxy.xml");
		}
		if (!Files.exists(cfgPath)) {
			LOGGER.fine("Fail2Ban config file not found (" + cfgPath + "). Using defaults.");
			return null;
		}
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(Files.newInputStream(cfgPath));
		
		NodeList fail2banList = doc.getElementsByTagName("fail2ban");
		if (fail2banList.getLength() == 0) {
			LOGGER.fine("No <fail2ban> element found in " + CONFIG_FILE + ". Using defaults.");
			return null;
		}
		
		return (Element) fail2banList.item(0);
	}
	
	private String[] loadIgnoreIps(Element cfgElement) {
		NodeList ipNodes = cfgElement.getElementsByTagName("ip");
		String[] ips = new String[ipNodes.getLength()];
		for (int i = 0; i < ipNodes.getLength(); i++) {
			Element ipElem = (Element) ipNodes.item(i);
			ips[i] = ipElem.getAttribute("value");
		}
		return ips.length > 0 ? ips : new String[] { "127.0.0.1", "0:0:0:0:0:0:0:1" };
	}
	
	private Map<String, Jail> loadJails(Element cfgElement) {
		Map<String, Jail> m = new HashMap<>();
		NodeList jailNodes = cfgElement.getElementsByTagName("jail");
		
		for (int i = 0; i < jailNodes.getLength(); i++) {
			Element jailElem = (Element) jailNodes.item(i);
			String name = jailElem.getAttribute("name");
			boolean enabled = Boolean.parseBoolean(jailElem.getAttribute("enabled"));
			long findTimeMs = Long.parseLong(jailElem.getAttribute("findTimeMs"));
			int maxRetry = Integer.parseInt(jailElem.getAttribute("maxRetry"));
			long banTimeMs = Long.parseLong(jailElem.getAttribute("banTimeMs"));
			
			Jail j = Jail.builder(name)
				.id(i + 1)
				.findTimeMs(findTimeMs)
				.maxRetry(maxRetry)
				.banTimeMs(banTimeMs)
				.enabled(enabled)
				.build();
			m.put(name, j);
		}
		
		return m.isEmpty() ? createDefaultJails() : m;
	}
	
	private static Map<String, Jail> createDefaultJails() {
		Map<String, Jail> m = new HashMap<>();
		
		m.put("bruteforce", Jail.builder("bruteforce")
			.id(1)
			.findTimeMs(60000)
			.maxRetry(5)
			.banTimeMs(3600000)
			.enabled(true)
			.build());
		
		m.put("scanner", Jail.builder("scanner")
			.id(2)
			.findTimeMs(30000)
			.maxRetry(10)
			.banTimeMs(86400000)
			.enabled(true)
			.build());
		
		m.put("dos_flood", Jail.builder("dos_flood")
			.id(3)
			.findTimeMs(60000)
			.maxRetry(3)
			.banTimeMs(7200000)
			.enabled(true)
			.build());
		
		return m;
	}
	
	private void logConfiguration() {
		LOGGER.fine("=== Fail2Ban Configuration ===");
		LOGGER.fine(String.format("  Enabled: %s", enabled));
		LOGGER.fine(String.format("  Firewall: %s", firewallEnabled));
		LOGGER.fine(String.format("  Persistence: %s (%s)", persistenceEnabled, persistencePath));
		LOGGER.fine(String.format("  Max Active Bans: %d", maxActiveBans));
		LOGGER.fine(String.format("  Ignore IPs: %d", ignoreIps.length));
		for (Jail j : jails.values()) {
			LOGGER.fine(String.format("  Jail '%s': %s", j.getName(), j));
		}
	}
	
	// Getters
	public boolean isEnabled() { return enabled; }
	public boolean isFirewallEnabled() { return firewallEnabled; }
	public boolean isPersistenceEnabled() { return persistenceEnabled; }
	public String getPersistencePath() { return persistencePath; }
	public int getMaxActiveBans() { return maxActiveBans; }
	public long getGcIntervalMs() { return gcIntervalMs; }
	public String[] getIgnoreIps() { return ignoreIps.clone(); }
	public Map<String, Jail> getJails() { return new HashMap<>(jails); }
	public Jail getJail(String name) { return jails.get(name); }
}
