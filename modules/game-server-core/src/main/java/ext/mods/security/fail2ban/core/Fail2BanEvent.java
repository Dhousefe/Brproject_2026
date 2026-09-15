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
 * Event emitted by BanManager and ProxySecurityBridge for UI listeners (e.g., Swing dashboard).
 * Immutable record capturing security and proxy traffic events with timestamp.
 * 
 * @param timestamp Milliseconds since epoch when event occurred
 * @param ip The affected IP address
 * @param jail The jail name or proxy route that triggered the event
 * @param type Type of event: "FAIL", "BAN", "UNBAN", "EXPIRE", "DROP", "REQ_PASS", "REQ_DROP"
 * @param detail Optional detail string (e.g., failure reason, ban duration, URI, rate limit)
 * @param payloadSample Truncated request payload or path snippet (for audit inspection)
 * @param status Semantic status display (e.g., "BLOQUEADO", "SUSPEITO", "PERMITIDO", "EXPIRADO")
 */
public record Fail2BanEvent(
	long timestamp,
	String ip,
	String jail,
	String type,
	String detail,
	String payloadSample,
	String status
) {
	/**
	 * Canonical legacy constructor for backward compatibility.
	 */
	public Fail2BanEvent(long timestamp, String ip, String jail, String type, String detail) {
		this(timestamp, ip, jail, type, detail, null, defaultStatus(type));
	}

	private static String defaultStatus(String type) {
		if (type == null) return "UNKNOWN";
		return switch (type.toUpperCase()) {
			case "BAN" -> "BLOQUEADO";
			case "FAIL", "FAILURE" -> "SUSPEITO";
			case "UNBAN" -> "DESBANIDO";
			case "EXPIRE" -> "EXPIRADO";
			case "DROP", "BAN_DROP" -> "BLOQUEADO";
			case "RATE_LIMIT_DENIED" -> "RATE_LIMIT";
			case "REQ_PASS", "PASS", "HTTP", "HTTPS", "TCP", "UDP", "WEBSOCKET", "WSS" -> "PERMITIDO";
			default -> type;
		};
	}

	/**
	 * Returns "IPv6" if IP contains ':' or "IPv4" otherwise.
	 */
	public String ipVersion() {
		if (ip == null || ip.isEmpty()) return "IPv4";
		return (ip.indexOf(':') >= 0) ? "IPv6" : "IPv4";
	}

	/**
	 * Creates a FAIL event (login failure, detection hit, etc).
	 */
	public static Fail2BanEvent failure(String ip, String jail, String detail) {
		return new Fail2BanEvent(System.currentTimeMillis(), ip, jail, "FAIL", detail, null, "SUSPEITO");
	}
	
	/**
	 * Creates a BAN event (threshold exceeded, auto-ban triggered).
	 */
	public static Fail2BanEvent ban(String ip, String jail, long durationMs) {
		String detail = durationMs < 0 ? "PERMANENT" : (durationMs / 1000) + "s";
		return new Fail2BanEvent(System.currentTimeMillis(), ip, jail, "BAN", detail, null, "BLOQUEADO");
	}
	
	/**
	 * Creates an UNBAN event (manual unban).
	 */
	public static Fail2BanEvent unban(String ip, String jail) {
		return new Fail2BanEvent(System.currentTimeMillis(), ip, jail, "UNBAN", "manual", null, "DESBANIDO");
	}
	
	/**
	 * Creates an EXPIRE event (ban naturally expired).
	 */
	public static Fail2BanEvent expire(String ip, String jail) {
		return new Fail2BanEvent(System.currentTimeMillis(), ip, jail, "EXPIRE", null, null, "EXPIRADO");
	}

	/**
	 * Creates a proxy-traffic or security event from the reverse proxy edge.
	 */
	public static Fail2BanEvent proxyEvent(String ip, String route, String type, String detail, String payloadSample) {
		return new Fail2BanEvent(System.currentTimeMillis(), ip, route, type, detail, payloadSample, defaultStatus(type));
	}

	/**
	 * Creates a live network traffic event for reverse proxy auditing.
	 */
	public static Fail2BanEvent trafficEvent(String ip, String route, String proto, String detail, String payloadSample, String status) {
		return new Fail2BanEvent(System.currentTimeMillis(), ip, route, proto, detail, payloadSample, status != null ? status : defaultStatus(proto));
	}
	
	/**
	 * Human-readable string for logging/UI.
	 */
	@Override
	public String toString() {
		return String.format("[%s] %s (%s) @ %s (%s) [%s]", type, ip, ipVersion(), jail, detail != null ? detail : "", status);
	}
}
