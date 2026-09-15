/*
 * BrProject — Phase 6 OSS.
 * Cosmetic product license gate retired; this type remains only as a no-op
 * compatibility shim for any external launcher still calling it.
 */
package ext.mods.security;

import ext.mods.commons.logging.CLogger;

/**
 * @deprecated Phase 6: no product license is enforced. Always returns open expiry.
 */
@Deprecated
public class LicenseValidator
{
	protected static CLogger LOGGER = new CLogger(LicenseValidator.class.getName());
	
	/**
	 * @return dummy local address (no outbound IP lookup)
	 */
	public static String getPublicIPAddress()
	{
		return "127.0.0.1";
	}
	
	/**
	 * Always grants access (OSS). Parameters ignored.
	 * @return non-null expiry label
	 */
	public static String checkLicenseAndGetExpiry(String ip, String key, String email)
	{
		return "OSS-open";
	}
}
