package ext.mods.loginserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthRulesTest
{
	@Test
	void permanentlyBanned_negativeAccess()
	{
		assertTrue(AuthRules.isPermanentlyBanned(-1));
		assertFalse(AuthRules.isPermanentlyBanned(0));
		assertFalse(AuthRules.isPermanentlyBanned(100));
	}
	
	@Test
	void permanentlyBanned_extremeNegative()
	{
		assertTrue(AuthRules.isPermanentlyBanned(Integer.MIN_VALUE));
		assertFalse(AuthRules.isPermanentlyBanned(Integer.MAX_VALUE));
	}
	
	@Test
	void shouldBanAfterFailedAttempts()
	{
		assertFalse(AuthRules.shouldBanAfterFailedAttempts(2, 3));
		assertTrue(AuthRules.shouldBanAfterFailedAttempts(3, 3));
		assertTrue(AuthRules.shouldBanAfterFailedAttempts(10, 3));
		assertFalse(AuthRules.shouldBanAfterFailedAttempts(100, 0));
	}
	
	@Test
	void shouldBanAfterFailedAttempts_thresholdOneAndNegativeMax()
	{
		assertFalse(AuthRules.shouldBanAfterFailedAttempts(0, 1));
		assertTrue(AuthRules.shouldBanAfterFailedAttempts(1, 1));
		assertFalse(AuthRules.shouldBanAfterFailedAttempts(5, -1));
	}
	
	@Test
	void banStillActive()
	{
		assertTrue(AuthRules.isBanStillActive(0L, System.currentTimeMillis()));
		final long now = 1_000_000L;
		assertTrue(AuthRules.isBanStillActive(now + 1, now));
		assertFalse(AuthRules.isBanStillActive(now - 1, now));
	}
	
	@Test
	void banStillActive_expiresExactlyAtNow()
	{
		final long now = 5_000_000L;
		// banUntil must be strictly greater than now to remain active
		assertFalse(AuthRules.isBanStillActive(now, now));
	}
	
	@Test
	void normalizeLogin()
	{
		assertEquals("player", AuthRules.normalizeLogin("Player"));
		assertEquals("abc", AuthRules.normalizeLogin("ABC"));
	}
	
	@Test
	void normalizeLogin_nullAndEmpty()
	{
		assertNull(AuthRules.normalizeLogin(null));
		assertEquals("", AuthRules.normalizeLogin(""));
		assertEquals("mixed.case", AuthRules.normalizeLogin("MiXeD.CaSe"));
	}
}
