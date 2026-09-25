package ext.mods.loginserver.data.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ext.mods.loginserver.data.repository.AccountStore;
import ext.mods.loginserver.model.Account;

class AccountTableTest
{
	@Test
	void delegatesAccountReadsToThePersistencePort()
	{
		final StubAccountStore store = new StubAccountStore();
		final AccountTable table = new AccountTable(store);

		assertSame(store.account, table.getAccount("magicarrow28"));
		assertEquals(7, table.getAccountCount());
		assertTrue(table.hasExternalIdentity("discord", "d123"));
		assertSame(store.account, table.getExternalAccount("discord", "123"));
	}

	@Test
	void delegatesAccountUpdatesAndExternalResolutionToThePersistencePort()
	{
		final StubAccountStore store = new StubAccountStore();
		final AccountTable table = new AccountTable(store);

		table.createAccount("new-account", "hash", 10L);
		assertEquals("new-account", store.createdLogin);
		assertTrue(table.setAccountLastTime("new-account", 20L));
		table.setAccountAccessLevel("new-account", 8);
		table.setAccountLastServer("new-account", 1);
		assertEquals(AccountStore.ExternalAuthStatus.SUCCESS, table.resolveOrCreateExternalAccount("discord", "123", "d123").getStatus());
		assertEquals("new-account", store.createdLogin);
	}

	private static final class StubAccountStore implements AccountStore
	{
		private final Account account = new Account("magicarrow28", "hash", 8, 1);
		private String createdLogin;

		@Override
		public Account getAccount(String login)
		{
			return account;
		}

		@Override
		public Account createAccount(String login, String hashedPassword, long currentTime)
		{
			createdLogin = login;
			return account;
		}

		@Override
		public boolean setAccountLastTime(String login, long currentTime)
		{
			return true;
		}

		@Override
		public void setAccountAccessLevel(String login, int level)
		{
		}

		@Override
		public void setAccountLastServer(String login, int serverId)
		{
		}

		@Override
		public int getAccountCount()
		{
			return 7;
		}

		@Override
		public boolean hasExternalIdentity(String provider, String login)
		{
			return true;
		}

		@Override
		public Account getExternalAccount(String provider, String providerUserId)
		{
			return account;
		}

		@Override
		public ExternalAuthResolution resolveOrCreateExternalAccount(String provider, String providerUserId, String derivedLogin)
		{
			return ExternalAuthResolution.success(account);
		}
	}
}
