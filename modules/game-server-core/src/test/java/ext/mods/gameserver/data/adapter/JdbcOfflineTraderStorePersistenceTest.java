package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.OfflineTradeItem;
import ext.mods.gameserver.data.repository.OfflineTraderData;

/** Optional PostgreSQL/MariaDB contract test for offline trader persistence. */
class JdbcOfflineTraderStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147482994;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement items = con.prepareStatement("DELETE FROM character_offline_trade_items WHERE charId=?"); PreparedStatement status = con.prepareStatement("DELETE FROM character_offline_trade WHERE charId=?"))
			{
				items.setInt(1, CHARACTER_ID);
				items.executeUpdate();
				status.setInt(1, CHARACTER_ID);
				status.executeUpdate();
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void replacesLoadsAndClearsTraderState() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the offline trader contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "OfflineTraderContractPool");
		final JdbcOfflineTraderStore store = new JdbcOfflineTraderStore();
		final OfflineTraderData trader = new OfflineTraderData(CHARACTER_ID, 123456789L, 1, "Contract Shop", List.of(new OfflineTradeItem(57, 10, 25, 3)));

		store.replaceAll(List.of(trader));
		assertEquals(List.of(trader), store.loadAll());

		store.replaceAll(List.of());
		assertTrue(store.loadAll().stream().noneMatch(value -> value.characterId() == CHARACTER_ID));
		assertTrue(store.loadAll().isEmpty());
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
