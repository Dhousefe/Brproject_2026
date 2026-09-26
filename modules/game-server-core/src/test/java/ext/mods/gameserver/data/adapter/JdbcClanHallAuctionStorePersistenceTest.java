package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ClanHallAuctionStore.BidRecord;

/** Optional PostgreSQL contract test for Clan Hall auction persistence. */
class JdbcClanHallAuctionStorePersistenceTest
{
	private static final int CLAN_HALL_ID = 2147483001;
	private static final int CLAN_ID = 2147483002;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			final JdbcClanHallAuctionStore store = new JdbcClanHallAuctionStore();
			store.deleteBid(CLAN_HALL_ID, CLAN_ID);
		}
		ConnectionPool.shutdown();
	}

	@Test
	void bidLifecycleUsesTheJdbcAdapter()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the Clan Hall auction persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "ClanHallAuctionStoreContractPool");

		final JdbcClanHallAuctionStore store = new JdbcClanHallAuctionStore();
		final BidRecord bid = new BidRecord(CLAN_HALL_ID, "auction-test", CLAN_ID, "auction-test-clan", 123456, System.currentTimeMillis());
		store.saveBid(bid);

		final BidRecord stored = store.loadBids(CLAN_HALL_ID).stream().filter(value -> value.clanId() == CLAN_ID).findFirst().orElseThrow();
		assertEquals(bid.bid(), stored.bid());
		assertEquals(bid.clanName(), stored.clanName());
		assertTrue(stored.bidTime() > 0);

		store.deleteBid(CLAN_HALL_ID, CLAN_ID);
		assertTrue(store.loadBids(CLAN_HALL_ID).stream().noneMatch(value -> value.clanId() == CLAN_ID));
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
