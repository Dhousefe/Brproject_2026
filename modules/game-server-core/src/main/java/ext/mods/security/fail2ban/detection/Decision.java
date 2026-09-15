/*
* Copyleft © 2024-2026 L2Brproject
*/
package ext.mods.security.fail2ban.detection;

/**
 * Decision outcome from DetectionEngine evaluation.
 */
public enum Decision {
	ALLOW,           // No threat, allow traffic
	RECORD_FAIL,     // Increment failure counter for this IP
	BAN_IMMEDIATE    // Ban immediately (high severity)
}
