package ext.mods.loginserver.auth;

/**
 * Pure auth rules for login (Phase 6 — unit-testable, no DB).
 */
public final class AuthRules
{
	private AuthRules()
	{
	}
	
	/** Negative access level means permanently banned. */
	public static boolean isPermanentlyBanned(int accessLevel)
	{
		return accessLevel < 0;
	}
	
	/** Whether failed attempt count should trigger an IP ban. */
	public static boolean shouldBanAfterFailedAttempts(int failedAttempts, int maxAttemptsBeforeBan)
	{
		if (maxAttemptsBeforeBan <= 0)
			return false;
		return failedAttempts >= maxAttemptsBeforeBan;
	}
	
	/** Ban until millis: 0 means permanent; otherwise absolute epoch deadline. */
	public static boolean isBanStillActive(long banUntilMillis, long nowMillis)
	{
		if (banUntilMillis == 0L)
			return true; // permanent
		return banUntilMillis > nowMillis;
	}
	
	public static String normalizeLogin(String login)
	{
		return login == null ? null : login.toLowerCase();
	}
}
