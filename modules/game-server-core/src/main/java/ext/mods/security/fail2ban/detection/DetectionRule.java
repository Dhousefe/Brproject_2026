/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.detection;

import java.util.regex.Pattern;

/**
 * A detection rule: regex pattern matched against payload to flag attacks.
 */
public class DetectionRule {
	private final String name;
	private final Pattern pattern;
	private final int severity; // 1-10: 1=warning, 10=ban immediately
	private final String jail;  // Which jail to increment counter in
	
	public DetectionRule(String name, String regex, int severity, String jail) {
		this.name = name;
		this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		this.severity = Math.max(1, Math.min(10, severity));
		this.jail = jail;
	}
	
	public boolean matches(String payload) {
		if (payload == null || payload.isEmpty()) return false;
		return pattern.matcher(payload).find();
	}
	
	public String getName() { return name; }
	public int getSeverity() { return severity; }
	public String getJail() { return jail; }
}
