package ext.mods.loginserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.DriverManager;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.loginserver.model.Account;

/**
 * Optional real-database contract test for the account/login adapter.
 *
 * <p>It is skipped unless DB_TEST_URL is provided, so ordinary unit builds do
 * not depend on a developer database.</p>
 */
class JdbcAccountStorePersistenceTest
{
	private String _login;

	@AfterEach
	void tearDown() throws Exception
	{
		if (_login != null)
		{
			try (var connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
				var statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
			{
				statement.setString(1, _login);
				statement.executeUpdate();
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void accountLifecycleUsesTheJdbcAdapter() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the account persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "AccountStoreContractPool");

		_login = "acct_" + UUID.randomUUID().toString().replace("-", "");
		final JdbcAccountStore store = new JdbcAccountStore();
		final Account created = store.createAccount(_login, "bcrypt-contract-hash", System.currentTimeMillis());

		assertNotNull(created);
		assertEquals(_login, store.getAccount(_login).getLogin());
		assertEquals(true, store.setAccountLastTime(_login, System.currentTimeMillis()));
		store.setAccountAccessLevel(_login, 8);
		store.setAccountLastServer(_login, 1);

		final Account reloaded = store.getAccount(_login);
		assertNotNull(reloaded);
		assertEquals(8, reloaded.getAccessLevel());
		assertEquals(1, reloaded.getLastServer());
	}

	private static String databaseUrl()
	{
		return firstNonBlank(System.getProperty("dbTestUrl"), System.getenv("DB_TEST_URL"));
	}

	private static String databaseUser()
	{
		return firstNonBlank(System.getProperty("dbTestUser"), System.getenv("DB_TEST_USER"), "brproject");
	}

	private static String databasePassword()
	{
		return firstNonBlank(System.getProperty("dbTestPassword"), System.getenv("DB_TEST_PASSWORD"), "brproject");
	}

	private static String firstNonBlank(String... values)
	{
		for (String value : values)
			if (value != null && !value.isBlank())
				return value;
		return null;
	}
}
