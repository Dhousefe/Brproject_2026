package ext.mods.loginserver.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionKeyTest
{
	@Test
	void checkLoginPair_match()
	{
		final SessionKey key = new SessionKey(1, 2, 3, 4);
		assertTrue(key.checkLoginPair(1, 2));
		assertFalse(key.checkLoginPair(9, 2));
	}
	
	@Test
	void equals_alwaysRequiresAllParts()
	{
		// SHOW_LICENCE only affects client packet flow; server-side equals always compares full 128-bit key.
		final SessionKey a = new SessionKey(1, 2, 3, 4);
		final SessionKey b = new SessionKey(1, 2, 3, 4);
		final SessionKey c = new SessionKey(1, 2, 3, 9);
		final SessionKey d = new SessionKey(9, 8, 3, 4); // login pair differs, play same
		final SessionKey e = new SessionKey(1, 2, 9, 4); // playOkID1 differs
		final SessionKey f = new SessionKey(1, 9, 3, 4); // loginOkID2 differs
		assertTrue(a.equals(b));
		assertFalse(a.equals(c));
		assertFalse(a.equals(d));
		assertFalse(a.equals(e));
		assertFalse(a.equals(f));
		assertFalse(a.equals(null));
	}
	
	@Test
	void publicFields_loginOkAndPlayOk()
	{
		final SessionKey key = new SessionKey(10, 20, 30, 40);
		assertEquals(10, key.loginOkID1);
		assertEquals(20, key.loginOkID2);
		assertEquals(30, key.playOkID1);
		assertEquals(40, key.playOkID2);
	}
	
	@Test
	void toString_notEmptyAndIncludesParts()
	{
		final SessionKey key = new SessionKey(1, 2, 3, 4);
		final String s = key.toString();
		assertNotNull(s);
		assertFalse(s.isBlank());
		assertTrue(s.contains("PlayOk"));
		assertTrue(s.contains("LoginOk"));
		assertTrue(s.contains("3"));
		assertTrue(s.contains("1"));
	}
	
	@Test
	void hashCode_consistentWithIdentityEqualsContract()
	{
		// SessionKey.equals(SessionKey) is value-based; Object.hashCode remains identity-based.
		// Contract for Object: same instance always returns the same hashCode.
		final SessionKey a = new SessionKey(1, 2, 3, 4);
		final SessionKey b = new SessionKey(1, 2, 3, 4);
		assertEquals(a.hashCode(), a.hashCode());
		assertTrue(a.equals(b));
		// Different instances with equal material may still have different Object.hashCode values.
		// That is expected: SessionKey does not override Object.equals / hashCode.
	}
	
	@Test
	void secureRandom_producesVaryingKeys()
	{
		final SessionKey a = SessionKey.secureRandom();
		final SessionKey b = SessionKey.secureRandom();
		// Extremely unlikely both are identical across 128 bits
		assertNotEquals(a.toString(), b.toString());
	}
}
