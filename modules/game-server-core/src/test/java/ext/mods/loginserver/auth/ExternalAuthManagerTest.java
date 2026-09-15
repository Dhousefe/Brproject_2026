package ext.mods.loginserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExternalAuthManagerTest
{
	@Test
	void deriveLogin_conformsToFermataSpecification()
	{
		// Official example from Fermata documentation:
		// Discord user ID 80351110224678912 -> dlz63ag7l4ao
		final String userId = "80351110224678912";
		final String derived = ExternalAuthManager.deriveLogin(userId);
		
		assertEquals("dlz63ag7l4ao", derived);
		assertTrue(ExternalAuthManager.isValidDerivedLogin(derived));
		assertTrue(derived.matches("^d[0-9a-z]{1,13}$"));
	}
	
	@Test
	void deriveLogin_handlesVariousSnowflakeIds()
	{
		// Test various snowflake ranges
		final String[] testIds = {
			"1",
			"80351110224678912",
			"175928847299117063",
			"1154567890123456789",
			"18446744073709551615" // uint64 max
		};
		
		for (String id : testIds)
		{
			final String login = ExternalAuthManager.deriveLogin(id);
			assertNotNull(login, "Login should not be null for valid ID: " + id);
			assertTrue(login.startsWith("d"), "Login should start with 'd': " + login);
			assertTrue(login.length() <= 14, "Login length must be <= 14 chars: " + login);
			assertTrue(ExternalAuthManager.isValidDerivedLogin(login), "Derived login format valid: " + login);
			assertTrue(login.matches("^d[0-9a-z]{1,13}$"));
		}
	}
	
	@Test
	void deriveLogin_rejectsInvalidInputs()
	{
		assertNull(ExternalAuthManager.deriveLogin(null));
		assertNull(ExternalAuthManager.deriveLogin(""));
		assertNull(ExternalAuthManager.deriveLogin("   "));
		assertNull(ExternalAuthManager.deriveLogin("-12345"));
		assertNull(ExternalAuthManager.deriveLogin("not_a_number"));
		assertNull(ExternalAuthManager.deriveLogin("12345abc"));
	}
	
	@Test
	void challengeGeneration_producesDeterministicStructure()
	{
		final ExternalAuthManager authMgr = ExternalAuthManager.getInstance();
		final int sessionId = 12345678;
		final byte[] challenge = authMgr.generateChallenge(sessionId);
		
		assertNotNull(challenge);
		// 2 (serverId length) + serverId.length + 4 (sessionId) + 8 (timestamp) + 16 (nonce) + 32 (HMAC-SHA256)
		assertTrue(challenge.length >= 62, "Challenge length must be at least 62 bytes (header + nonce + HMAC-SHA256)");
	}
}