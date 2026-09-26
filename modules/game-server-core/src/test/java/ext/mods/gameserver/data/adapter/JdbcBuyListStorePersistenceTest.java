package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.BuyListStore;

/** Optional PostgreSQL contract test for limited-stock NPC buy list persistence. */
class JdbcBuyListStorePersistenceTest
{
	private static final int BUY_LIST_ID = 2147483003;
	private static final int ITEM_ID = 2147483004;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM buylists WHERE buylist_id = ? AND item_id = ?"))
			{
				ps.setInt(1, BUY_LIST_ID);
				ps.setInt(2, ITEM_ID);
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
	void savesLoadsAndDeletesLimitedStockStateThroughTheAdapter()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the buy list persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "BuyListStoreContractPool");

		final JdbcBuyListStore store = new JdbcBuyListStore();
		final long nextRestockTime = System.currentTimeMillis() + 60000;
		store.saveRestock(new BuyListStore.RestockRecord(BUY_LIST_ID, ITEM_ID, 7, nextRestockTime));

		final BuyListStore.RestockRecord restock = store.loadRestocks().stream().filter(value -> value.buyListId() == BUY_LIST_ID && value.itemId() == ITEM_ID).findFirst().orElseThrow();
		assertEquals(7, restock.count());
		assertEquals(nextRestockTime, restock.nextRestockTime());

		store.deleteRestock(BUY_LIST_ID, ITEM_ID);
		assertTrue(store.loadRestocks().stream().noneMatch(value -> value.buyListId() == BUY_LIST_ID && value.itemId() == ITEM_ID));
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
