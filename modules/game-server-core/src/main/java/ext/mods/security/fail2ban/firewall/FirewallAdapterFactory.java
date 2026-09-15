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
package ext.mods.security.fail2ban.firewall;

import java.util.logging.Logger;

/**
 * Factory for auto-detecting and creating the appropriate OS firewall adapter.
 */
public class FirewallAdapterFactory {
	private static final Logger LOGGER = Logger.getLogger(FirewallAdapterFactory.class.getName());
	
	/**
	 * Detect and return the appropriate firewall adapter for this OS.
	 * Falls back to NoopFirewallAdapter if no external firewall is available.
	 */
	public static FirewallAdapter detect() {
		String os = System.getProperty("os.name", "").toLowerCase();
		FirewallAdapter adapter;
		
		if (os.contains("win")) {
			adapter = new WindowsFirewallAdapter();
			if (adapter.available()) {
				LOGGER.info("Firewall adapter selected: " + adapter.name());
				return adapter;
			}
		} else if (os.contains("linux")) {
			adapter = new LinuxIptablesAdapter();
			if (adapter.available()) {
				LOGGER.info("Firewall adapter selected: " + adapter.name());
				return adapter;
			}
		} else if (os.contains("mac")) {
			adapter = new MacOsPfctlAdapter();
			if (adapter.available()) {
				LOGGER.info("Firewall adapter selected: " + adapter.name());
				return adapter;
			}
		}
		
		// Fallback: no external firewall available
		LOGGER.warning("No external firewall adapter available on " + os + ". Using in-process rejection only.");
		return new NoopFirewallAdapter();
	}
}
