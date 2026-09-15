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

import java.util.List;

/**
 * Abstraction for OS-specific firewall implementations.
 * Each adapter handles ban/unban operations via native firewall APIs.
 */
public interface FirewallAdapter {
	
	/**
	 * Ban an IP address for the specified duration.
	 * 
	 * @param ip IP address to ban
	 * @param durationMs Duration in milliseconds (0 = permanent)
	 * @throws Exception if ban fails
	 */
	void ban(String ip, long durationMs) throws Exception;
	
	/**
	 * Unban an IP address.
	 * 
	 * @param ip IP address to unban
	 * @throws Exception if unban fails
	 */
	void unban(String ip) throws Exception;
	
	/**
	 * List all currently banned IPs managed by this adapter.
	 * 
	 * @return List of banned IPs
	 * @throws Exception if listing fails
	 */
	List<String> listBanned() throws Exception;
	
	/**
	 * Check if this adapter is available on the current system.
	 * 
	 * @return true if the adapter can be used, false otherwise
	 */
	boolean available();
	
	/**
	 * Get human-readable name of this adapter.
	 * 
	 * @return Name (e.g., "Windows Firewall", "iptables", "pfctl")
	 */
	String name();
}
