package ext.mods.loginserver.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AccountTest
{
	@Test
	void login_isLowerCased()
	{
		final Account a = new Account("UserName", "hash", 0, 1);
		assertEquals("username", a.getLogin());
		assertEquals("hash", a.getPassword());
		assertEquals(0, a.getAccessLevel());
		assertEquals(1, a.getLastServer());
	}
}
