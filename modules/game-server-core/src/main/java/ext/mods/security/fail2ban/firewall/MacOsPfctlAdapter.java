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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * macOS firewall adapter using pfctl (packet filter).
 * Requires table definition in /etc/pf.conf.
 */
public class MacOsPfctlAdapter implements FirewallAdapter {
	private static final Logger LOGGER = Logger.getLogger(MacOsPfctlAdapter.class.getName());
	private static final String TABLE_NAME = "brproject_ban";
	private static final int CMD_TIMEOUT_MS = 5000;
	
	@Override
	public void ban(String ip, long durationMs) throws Exception {
		execute(String.format("sudo pfctl -t %s -T add %s", TABLE_NAME, ip), "Ban", ip);
	}
	
	@Override
	public void unban(String ip) throws Exception {
		execute(String.format("sudo pfctl -t %s -T delete %s", TABLE_NAME, ip), "Unban", ip);
	}
	
	@Override
	public List<String> listBanned() throws Exception {
		List<String> banned = new ArrayList<>();
		
		try {
			Process p = new ProcessBuilder("sudo", "pfctl", "-t", TABLE_NAME, "-T", "show").start();
			if (!p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
			}
			
			try (java.util.Scanner scanner = new java.util.Scanner(p.getInputStream())) {
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine().trim();
					if (!line.isEmpty()) {
						banned.add(line);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to list banned IPs", e);
		}
		
		return banned;
	}
	
	@Override
	public boolean available() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (!os.contains("mac")) {
			return false;
		}
		
		// Check if pfctl is available
		try {
			Process p = new ProcessBuilder("which", "pfctl").start();
			return p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
	
	@Override
	public String name() {
		return "macOS Firewall (pfctl)";
	}
	
	private void execute(String cmd, String action, String ip) throws Exception {
		try {
			Process p = new ProcessBuilder("bash", "-c", cmd).start();
			
			if (!p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
				throw new Exception("Command timeout");
			}
			
			if (p.exitValue() != 0) {
				LOGGER.warning("Command failed (requires sudo privileges): " + cmd);
				throw new Exception("Command failed with exit code " + p.exitValue());
			}
			
			LOGGER.fine("[" + action + "] " + ip + " via macOS firewall");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "[" + action + "] " + ip + " failed", e);
			throw e;
		}
	}
}
