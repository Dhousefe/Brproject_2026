/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.detection;

import java.util.ArrayList;
import java.util.List;

/**
 * Detection engine: applies rules to payload and returns decision.
 */
public class DetectionEngine {
	private final List<DetectionRule> rules;
	
	public DetectionEngine() {
		this.rules = new ArrayList<>();
		initializeDefaultRules();
	}
	
	/**
	 * Evaluate payload and return decision.
	 */
	public Decision evaluate(String ip, String payload) {
		if (payload == null || payload.isEmpty()) {
			return Decision.ALLOW;
		}
		
		int maxSeverity = 0;
		DetectionRule maxRule = null;
		
		for (DetectionRule rule : rules) {
			if (rule.matches(payload)) {
				if (rule.getSeverity() > maxSeverity) {
					maxSeverity = rule.getSeverity();
					maxRule = rule;
				}
			}
		}
		
		if (maxRule == null) {
			return Decision.ALLOW;
		}
		
		// Severity >= 8 = ban immediately
		if (maxSeverity >= 8) {
			return Decision.BAN_IMMEDIATE;
		}
		
		// Otherwise record failure
		return Decision.RECORD_FAIL;
	}
	
	private void initializeDefaultRules() {
		// SQL Injection patterns
		rules.add(new DetectionRule("sql_union", "union\\s+(select|all)", 9, "scanner"));
		rules.add(new DetectionRule("sql_or", "(or|and)\\s+1\\s*=\\s*1", 8, "scanner"));
		rules.add(new DetectionRule("sql_comment", "--|/\\\\*|\\\\*/", 7, "scanner"));
		
		// XSS patterns
		rules.add(new DetectionRule("xss_script", "\\u003cscript", 9, "scanner"));
		rules.add(new DetectionRule("xss_event", "on\\w+\\s*=", 8, "scanner"));
		
		// Path traversal
		rules.add(new DetectionRule("path_traversal", "\\\\.\\\\./", 7, "scanner"));
		
		// Scanner signatures
		rules.add(new DetectionRule("scanner_nmap", "nmap|nikto|sqlmap", 8, "scanner"));
		
		// Brute force (login failures)
		rules.add(new DetectionRule("login_fail", "(invalid|wrong|incorrect|failed).*password|unauthorized", 5, "bruteforce"));
	}
	
	public void addRule(DetectionRule rule) {
		rules.add(rule);
	}
	
	public List<DetectionRule> getRules() {
		return new ArrayList<>(rules);
	}
}
