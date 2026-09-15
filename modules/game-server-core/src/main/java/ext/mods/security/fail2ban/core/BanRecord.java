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

/**
 * Immutable record of a single ban entry.
 * 
 * @param id Database row ID (unique identifier)
 * @param ip The banned IP address (IPv4 or IPv6)
 * @param jailName Name of the jail that triggered the ban
 * @param reason Human-readable reason for the ban (e.g., "Brute force login")
 * @param banTime Timestamp when ban was created (milliseconds)
 * @param expireTime Timestamp when ban expires (-1 = permanent)
 * @param jailId Foreign key to jail configuration
 */
public record BanRecord(
	long id,
	String ip,
	String jailName,
	String reason,
	long banTime,
	long expireTime,
	int jailId
) {
	
	/**
	 * Factory method for a permanent ban.
	 */
	public static BanRecord permanent(String ip, String jailName, String reason, int jailId) {
		return new BanRecord(-1, ip, jailName, reason, System.currentTimeMillis(), -1L, jailId);
	}
	
	/**
	 * Factory method for a temporary ban.
	 * 
	 * @param ip The banned IP
	 * @param jailName Jail name
	 * @param reason Ban reason
	 * @param durationMs Duration in milliseconds from now
	 * @param jailId Jail ID
	 */
	public static BanRecord temporary(String ip, String jailName, String reason, long durationMs, int jailId) {
		final long now = System.currentTimeMillis();
		return new BanRecord(-1, ip, jailName, reason, now, now + durationMs, jailId);
	}
	
	/**
	 * Checks if this ban is still active (not expired).
	 */
	public boolean isActive() {
		if (expireTime < 0) return true; // permanent
		return System.currentTimeMillis() < expireTime;
	}
	
	/**
	 * Checks if this ban is permanently banned (expireTime == -1).
	 */
	public boolean isPermanent() {
		return expireTime < 0;
	}
	
	/**
	 * Returns remaining time in milliseconds, or -1 if permanent.
	 */
	public long getRemainingMs() {
		if (expireTime < 0) return -1;
		return Math.max(0, expireTime - System.currentTimeMillis());
	}
}
