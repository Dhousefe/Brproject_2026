package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.PremiumStore.PremiumRecord;

/** Optional PostgreSQL contract test for account premium persistence. */
class JdbcPremiumStorePersistenceTest
{
	private static final String ACCOUNT = "premium-store-contract";

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM account_premium WHERE account_name=?"))
			{
				ps.setString(1, ACCOUNT);
				ps.executeUpdate();
			}
			catch (Exception ignored)
			{
				// The contract test must not hide its primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void managesPremiumStateThroughTheAdapter() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the premium persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "PremiumStoreContractPool");

		final JdbcPremiumStore store = new JdbcPremiumStore();
		assertTrue(store.find(ACCOUNT).isEmpty());

		store.upsert(ACCOUNT, 1, 123456789L);
		assertEquals(new PremiumRecord(1, 123456789L), store.find(ACCOUNT).orElseThrow());

		store.upsert(ACCOUNT, 1, 987654321L);
		assertEquals(new PremiumRecord(1, 987654321L), store.find(ACCOUNT).orElseThrow());

		store.expire(ACCOUNT);
		assertEquals(new PremiumRecord(0, 0L), store.find(ACCOUNT).orElseThrow());

		store.delete(ACCOUNT);
		assertFalse(store.find(ACCOUNT).isPresent());
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
