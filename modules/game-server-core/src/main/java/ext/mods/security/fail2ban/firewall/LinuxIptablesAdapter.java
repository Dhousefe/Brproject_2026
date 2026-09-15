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
 * Linux firewall adapter using ipset + iptables.
 * Falls back to iptables-only if ipset is unavailable.
 */
public class LinuxIptablesAdapter implements FirewallAdapter {
	private static final Logger LOGGER = Logger.getLogger(LinuxIptablesAdapter.class.getName());
	private static final String IPSET_NAME = "brproject_ban";
	private static final int CMD_TIMEOUT_MS = 5000;
	
	private final boolean useIpset;
	
	public LinuxIptablesAdapter() {
		this.useIpset = checkIpsetAvailable();
		if (!useIpset) {
			LOGGER.warning("ipset not available, falling back to iptables-only");
		}
	}
	
	@Override
	public void ban(String ip, long durationMs) throws Exception {
		if (useIpset) {
			banWithIpset(ip, durationMs);
		} else {
			banWithIptables(ip);
		}
	}
	
	@Override
	public void unban(String ip) throws Exception {
		if (useIpset) {
			unbanWithIpset(ip);
		} else {
			unbanWithIptables(ip);
		}
	}
	
	@Override
	public List<String> listBanned() throws Exception {
		List<String> banned = new ArrayList<>();
		
		if (useIpset) {
			try {
				Process p = new ProcessBuilder("ipset", "list", IPSET_NAME).start();
				if (!p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
					p.destroyForcibly();
				}
				
				try (java.util.Scanner scanner = new java.util.Scanner(p.getInputStream())) {
					boolean inMembers = false;
					while (scanner.hasNextLine()) {
						String line = scanner.nextLine();
						if (line.startsWith("Members:")) {
							inMembers = true;
						} else if (inMembers && !line.isEmpty() && !line.startsWith(" ")) {
							inMembers = false;
						} else if (inMembers) {
							banned.add(line.trim());
						}
					}
				}
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to list ipset members", e);
			}
		}
		
		return banned;
	}
	
	@Override
	public boolean available() {
		String os = System.getProperty("os.name", "").toLowerCase();
		return os.contains("linux");
	}
	
	@Override
	public String name() {
		return useIpset ? "Linux (ipset + iptables)" : "Linux (iptables-only)";
	}
	
	private void banWithIpset(String ip, long durationMs) throws Exception {
		// Ensure ipset exists
		try {
			long timeoutSecs = Math.max(1, durationMs / 1000);
			Process p = new ProcessBuilder("bash", "-c",
				"ipset create " + IPSET_NAME + " hash:ip timeout " + timeoutSecs + " 2>/dev/null || true"
			).start();
			p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to create ipset", e);
		}
		
		// Add IP to ipset
		execute("ipset add " + IPSET_NAME + " " + ip, "Ban", ip);
	}
	
	private void unbanWithIpset(String ip) throws Exception {
		execute("ipset del " + IPSET_NAME + " " + ip, "Unban", ip);
	}
	
	private void banWithIptables(String ip) throws Exception {
		// Simple iptables rule (permanent, no timeout)
		execute("iptables -I INPUT -s " + ip + " -j DROP", "Ban", ip);
	}
	
	private void unbanWithIptables(String ip) throws Exception {
		execute("iptables -D INPUT -s " + ip + " -j DROP", "Unban", ip);
	}
	
	private boolean checkIpsetAvailable() {
		try {
			Process p = new ProcessBuilder("which", "ipset").start();
			return p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
	
	private void execute(String cmd, String action, String ip) throws Exception {
		try {
			Process p = new ProcessBuilder("bash", "-c", cmd).start();
			
			if (!p.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
				throw new Exception("Command timeout");
			}
			
			if (p.exitValue() != 0) {
				throw new Exception("Command failed with exit code " + p.exitValue());
			}
			
			LOGGER.fine("[" + action + "] " + ip + " via Linux firewall");
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "[" + action + "] " + ip + " failed", e);
			throw e;
		}
	}
}
