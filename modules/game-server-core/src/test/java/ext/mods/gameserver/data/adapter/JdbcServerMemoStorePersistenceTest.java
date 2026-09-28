package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;

/** Optional PostgreSQL/MariaDB contract test for global server memo persistence. */
class JdbcServerMemoStorePersistenceTest
{
	private static final String KEY = "server-memo-contract";

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM server_memo WHERE var=?"))
			{
				ps.setString(1, KEY);
				ps.executeUpdate();
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void upsertLoadsUpdatesAndDeletesServerMemo() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the server memo contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "ServerMemoContractPool");
		final JdbcServerMemoStore store = new JdbcServerMemoStore();

		store.upsert(KEY, "first");
		assertEquals("first", store.load().get(KEY));

		store.upsert(KEY, "second");
		assertEquals("second", store.load().get(KEY));

		store.delete(KEY);
		assertFalse(store.load().containsKey(KEY));
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
