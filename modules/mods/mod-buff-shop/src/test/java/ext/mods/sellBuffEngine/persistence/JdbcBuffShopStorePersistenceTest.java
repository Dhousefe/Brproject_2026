package ext.mods.sellBuffEngine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.enums.actors.Sex;
import ext.mods.gameserver.model.actor.container.player.Appearance;
import ext.mods.sellBuffEngine.ShopObject;

/** Optional PostgreSQL contract test for custom buff shop persistence. */
class JdbcBuffShopStorePersistenceTest
{
	private static final int OWNER_ID = 2147483003;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM buffshop WHERE ownerId = ?"))
			{
				ps.setInt(1, OWNER_ID);
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
	void savesAndLoadsBuffShopThroughTheAdapter()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the buff shop persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "BuffShopStoreContractPool");

		final ShopObject shop = new ShopObject(OWNER_ID);
		shop.setTitle("Contract Shop");
		shop.setStoreMessage("Contract Buffs");
		shop.setXYZ(100, 200, 300, 400);
		shop.setClassId(50);
		shop.setAppearance(new Appearance((byte) 0, (byte) 0, (byte) 0, Sex.VALUES[0]));
		shop.setEquippedItems(List.of(1001, 1002));
		shop.addBuff(1204, 2, 500);

		final JdbcBuffShopStore store = new JdbcBuffShopStore();
		store.saveShop(shop);

		final ShopObject loaded = store.loadShops().stream().filter(candidate -> candidate.getOwnerId() == OWNER_ID).findFirst().orElseThrow();
		assertEquals("Contract Shop", loaded.getTitle());
		assertEquals("Contract Buffs", loaded.getStoreMessage());
		assertEquals(100, loaded.getX());
		assertEquals(List.of(1001, 1002), loaded.getEquippedItems());
		assertTrue(loaded.getBuffList().containsKey(1204));
		assertEquals(500, loaded.getBuff(1204).price());
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
