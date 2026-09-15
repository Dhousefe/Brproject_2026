package ext.mods.commons.crypt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BCryptTest
{
	@Test
	void hashAndCheck_matchingPassword()
	{
		final String hash = BCrypt.hashPw("secret-pass");
		assertTrue(BCrypt.checkPw("secret-pass", hash));
	}
	
	@Test
	void checkPw_rejectsWrongPassword()
	{
		final String hash = BCrypt.hashPw("secret-pass");
		assertFalse(BCrypt.checkPw("wrong", hash));
	}
	
	@Test
	void hashPw_differentSalts_produceDifferentHashes()
	{
		final String a = BCrypt.hashPw("same");
		final String b = BCrypt.hashPw("same");
		assertNotEquals(a, b);
		assertTrue(BCrypt.checkPw("same", a));
		assertTrue(BCrypt.checkPw("same", b));
	}
	
	@Test
	void generateSalt_invalidRounds_throws()
	{
		assertThrows(IllegalArgumentException.class, () -> BCrypt.generateSalt(31));
	}
	
	@Test
	void generateSalt_lowRounds_usable()
	{
		final String salt = BCrypt.generateSalt(4);
		assertTrue(salt.startsWith("$2a$"));
		final String hash = BCrypt.hashPw("x", salt);
		assertTrue(BCrypt.checkPw("x", hash));
	}
}
