package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.ItemRecord;
import ext.mods.gameserver.data.repository.ItemSaveRecord;
import ext.mods.gameserver.enums.items.ItemLocation;

/** Optional PostgreSQL contract test for item and augmentation persistence. */
class JdbcItemStorePersistenceTest
{
	private static final int ITEM_OBJECT_ID = 2147482996;
	private static final int PET_ITEM_OBJECT_ID = 2147482995;
	private final JdbcItemStore store = new JdbcItemStore();

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM augmentations WHERE item_oid IN (?,?)"))
				{
					ps.setInt(1, ITEM_OBJECT_ID);
					ps.setInt(2, PET_ITEM_OBJECT_ID);
					ps.executeUpdate();
				}
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM pets WHERE item_obj_id=?"))
				{
					ps.setInt(1, PET_ITEM_OBJECT_ID);
					ps.executeUpdate();
				}
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM items WHERE object_id IN (?,?)"))
				{
					ps.setInt(1, ITEM_OBJECT_ID);
					ps.setInt(2, PET_ITEM_OBJECT_ID);
					ps.executeUpdate();
				}
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void savesRestoresAndRemovesAugmentedItems()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the item persistence contract test");
		try
		{
			ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "ItemStoreContractPool");

			store.saveItems(List.of(new ItemSaveRecord(ITEM_OBJECT_ID, 57, 12, 3, 42, 1, 2, ItemLocation.INVENTORY, 7, -1, 1234L, 16283, 3240, 10, false, false, false)));
			ItemRecord saved = store.loadItems(42, ItemLocation.INVENTORY).stream().findFirst().orElseThrow();
			assertEquals(12, saved.count());
			assertEquals(16283, saved.augmentationId());
			assertEquals(3240, saved.augmentationSkillId());

			store.saveItems(List.of(new ItemSaveRecord(ITEM_OBJECT_ID, 57, 20, 3, 42, 1, 2, ItemLocation.INVENTORY, 7, -1, 1235L, null, null, null, false, true, false)));
			saved = store.loadItems(42, ItemLocation.INVENTORY).stream().findFirst().orElseThrow();
			assertEquals(20, saved.count());
			assertNull(saved.augmentationId());
		}
		catch (Exception e)
		{
			throw new AssertionError("Item persistence contract failed", e);
		}
	}

	@Test
	void deletesPetStateThroughTheRepository()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the item persistence contract test");
		try
		{
			ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "ItemStorePetContractPool");
			try (Connection con = ConnectionPool.getConnection())
			{
				try (PreparedStatement ps = con.prepareStatement("INSERT INTO pets (item_obj_id) VALUES (?)"))
				{
					ps.setInt(1, PET_ITEM_OBJECT_ID);
					ps.executeUpdate();
				}
			}
			store.deletePet(PET_ITEM_OBJECT_ID);
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM pets WHERE item_obj_id=?"))
			{
				ps.setInt(1, PET_ITEM_OBJECT_ID);
				try (var rs = ps.executeQuery())
				{
					rs.next();
					assertEquals(0, rs.getInt(1));
				}
			}
		}
		catch (Exception e)
		{
			throw new AssertionError("Pet persistence contract failed", e);
		}
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
