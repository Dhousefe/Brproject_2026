package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ClanStore.WarRecord;

/** Optional PostgreSQL contract test for the clan persistence adapter. */
class JdbcClanStorePersistenceTest
{
	private static final int CLAN_ID_1 = 2147483001;
	private static final int CLAN_ID_2 = 2147483002;

	@AfterEach
	void tearDown() throws Exception
	{
		if (databaseUrl() != null)
		{
			try (var connection = DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
				var statement = connection.prepareStatement("DELETE FROM clan_wars WHERE clan1 IN (?, ?) OR clan2 IN (?, ?)"))
			{
				statement.setString(1, Integer.toString(CLAN_ID_1));
				statement.setString(2, Integer.toString(CLAN_ID_2));
				statement.setString(3, Integer.toString(CLAN_ID_1));
				statement.setString(4, Integer.toString(CLAN_ID_2));
				statement.executeUpdate();
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void clanWarLifecycleUsesTheJdbcAdapter() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the clan persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "ClanStoreContractPool");

		final JdbcClanStore store = new JdbcClanStore();
		store.saveWar(CLAN_ID_1, CLAN_ID_2);
		assertTrue(store.loadWars(System.currentTimeMillis()).stream().anyMatch(war -> isTestWar(war) && war.expiryTime() == 0));

		final long expiry = System.currentTimeMillis() + 60000;
		store.updateWarExpiry(CLAN_ID_1, CLAN_ID_2, expiry);
		final WarRecord updated = store.loadWars(System.currentTimeMillis()).stream().filter(this::isTestWar).findFirst().orElseThrow();
		assertEquals(expiry, updated.expiryTime());

		store.deleteWar(CLAN_ID_1, CLAN_ID_2);
		assertFalse(store.loadWars(System.currentTimeMillis()).stream().anyMatch(this::isTestWar));
	}

	private boolean isTestWar(WarRecord war)
	{
		return war.clanId1() == CLAN_ID_1 && war.clanId2() == CLAN_ID_2;
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
