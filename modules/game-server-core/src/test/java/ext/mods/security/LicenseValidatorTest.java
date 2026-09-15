package ext.mods.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Phase 6: cosmetic license gate must never block OSS boot.
 */
class LicenseValidatorTest
{
	@Test
	void checkLicense_alwaysOpen_regardlessOfEmail()
	{
		assertNotNull(LicenseValidator.checkLicenseAndGetExpiry(null, null, null));
		assertNotNull(LicenseValidator.checkLicenseAndGetExpiry("1.2.3.4", "any", "other@example.com"));
		assertEquals("OSS-open", LicenseValidator.checkLicenseAndGetExpiry("x", "y", "brprojeto@l2jbrasil.com"));
	}
	
	@Test
	void publicIp_noOutboundLookup()
	{
		assertEquals("127.0.0.1", LicenseValidator.getPublicIPAddress());
	}
}
