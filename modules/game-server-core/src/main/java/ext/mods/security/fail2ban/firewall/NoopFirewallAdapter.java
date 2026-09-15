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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * No-op firewall adapter: when external firewall is unavailable.
 * Bans are still enforced in-process via Netty handler.
 */
public class NoopFirewallAdapter implements FirewallAdapter {
	private static final Logger LOGGER = Logger.getLogger(NoopFirewallAdapter.class.getName());
	
	@Override
	public void ban(String ip, long durationMs) throws Exception {
		LOGGER.info("[No-op] Ban " + ip + " (external firewall unavailable, in-process rejection active)");
	}
	
	@Override
	public void unban(String ip) throws Exception {
		LOGGER.info("[No-op] Unban " + ip);
	}
	
	@Override
	public List<String> listBanned() throws Exception {
		return new ArrayList<>();
	}
	
	@Override
	public boolean available() {
		return false; // Explicitly unavailable
	}
	
	@Override
	public String name() {
		return "No-op (in-process rejection only)";
	}
}
