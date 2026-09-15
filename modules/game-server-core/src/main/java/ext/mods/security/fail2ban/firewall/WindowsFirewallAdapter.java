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
 * Windows Firewall adapter using netsh advfirewall.
 */
public class WindowsFirewallAdapter implements FirewallAdapter {
	private static final Logger LOGGER = Logger.getLogger(WindowsFirewallAdapter.class.getName());
	private static final String RULE_PREFIX = "BrProject-Ban-";
	private static final int CMD_TIMEOUT_MS = 5000;
	
	@Override
	public void ban(String ip, long durationMs) throws Exception {
		String ruleName = RULE_PREFIX + ip.replace(":", "_");
		String cmd = String.format(
			"netsh advfirewall firewall add rule name=\"%s\" dir=in action=block remoteip=%s",
			ruleName, ip
		);
		
		execute(cmd, "Ban", ip);
	}
	
	@Override
	public void unban(String ip) throws Exception {
		String ruleName = RULE_PREFIX + ip.replace(":", "_");
		String cmd = String.format(
			"netsh advfirewall firewall delete rule name=\"%s\"",
			ruleName
		);
		
		execute(cmd, "Unban", ip);
	}
	
	@Override
	public List<String> listBanned() throws Exception {
		List<String> banned = new ArrayList<>();
		String cmd = "netsh advfirewall firewall show rule name=all dir=in";
		
		try {
			ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
			Process p = pb.start();
			
			if (!p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
				throw new Exception("Command timeout");
			}
			
			// Parse output to extract banned IPs (simplified)
			try (java.util.Scanner scanner = new java.util.Scanner(p.getInputStream())) {
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					if (line.contains(RULE_PREFIX)) {
						// Extract IP from rule name
						int idx = line.indexOf(RULE_PREFIX);
						if (idx != -1) {
							String ip = line.substring(idx + RULE_PREFIX.length());
							ip = ip.replace("_", ":").split("[^0-9a-fA-F:.]")[0];
							if (!ip.isEmpty()) {
								banned.add(ip);
							}
						}
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
		// Check if we're on Windows
		String os = System.getProperty("os.name", "").toLowerCase();
		if (!os.contains("win")) {
			return false;
		}
		
		// Check if netsh is available
		try {
			Process p = new ProcessBuilder("netsh", "--version").start();
			return p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			return false;
		}
	}
	
	@Override
	public String name() {
		return "Windows Firewall (netsh)";
	}
	
	private void execute(String cmd, String action, String ip) throws Exception {
		try {
			ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
			Process p = pb.start();
			
			if (!p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
				throw new Exception("Command timeout");
			}
			
			if (p.exitValue() != 0) {
				throw new Exception("Command failed with exit code " + p.exitValue());
			}
			
			LOGGER.fine("[" + action + "] " + ip + " via Windows Firewall");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "[" + action + "] " + ip + " failed", e);
			throw e;
		}
	}
}
