package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.HennaRecord;
import ext.mods.gameserver.data.repository.MacroRecord;
import ext.mods.gameserver.data.repository.MemoRecord;
import ext.mods.gameserver.data.repository.MissionRecord;
import ext.mods.gameserver.data.repository.ShortcutRecord;

/** Optional PostgreSQL contract test for player auxiliary state. */
class JdbcPlayerAuxiliaryStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147482994;
    private final JdbcPlayerAuxiliaryStore store = new JdbcPlayerAuxiliaryStore();

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				for (String table : List.of("character_macroses", "character_shortcuts", "character_recipebook", "character_hennas", "character_mission", "character_memo"))
				{
					final String column = table.equals("character_mission") ? "object_id" : table.equals("character_macroses") || table.equals("character_shortcuts") || table.equals("character_hennas") ? "char_obj_id" : "charId";
					try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE " + column + "=?"))
					{
						ps.setInt(1, CHARACTER_ID);
						ps.executeUpdate();
					}
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
	void persistsMacrosShortcutsRecipesHennasMissionsAndMemos()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the auxiliary persistence contract test");
		try
		{
			ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "PlayerAuxiliaryContractPool");

			store.saveMacro(CHARACTER_ID, new MacroRecord(1000, 1, "Test", "Description", "T", "3,1,2,/sit;"));
			assertEquals("Test", store.loadMacros(CHARACTER_ID).get(0).name());

			store.saveShortcut(CHARACTER_ID, new ShortcutRecord(1, 0, "SKILL", 99, 1, 0));
			assertEquals(99, store.loadShortcuts(CHARACTER_ID, 0).get(0).id());

			store.addRecipe(CHARACTER_ID, 1);
			assertEquals(List.of(1), store.loadRecipes(CHARACTER_ID));

			store.addHenna(CHARACTER_ID, new HennaRecord(1, 1), 0);
			assertEquals(1, store.loadHennas(CHARACTER_ID, 0).get(0).symbolId());

			store.saveMissions(CHARACTER_ID, List.of(new MissionRecord("BAIUM", 1, 7)));
			assertEquals(7, store.loadMissions(CHARACTER_ID).get(0).value());

			store.saveMemo(CHARACTER_ID, new MemoRecord("test", "value"));
			assertEquals("value", store.loadMemos(CHARACTER_ID).get(0).value());

			store.deleteShortcut(CHARACTER_ID, new ShortcutRecord(1, 0, "SKILL", 99, 1, 0));
			store.deleteRecipe(CHARACTER_ID, 1);
			store.deleteHenna(CHARACTER_ID, 1, 0);
			store.deleteMemo(CHARACTER_ID, "test");
			assertEquals(List.of(), store.loadShortcuts(CHARACTER_ID, 0));
			assertEquals(List.of(), store.loadRecipes(CHARACTER_ID));
			assertEquals(List.of(), store.loadHennas(CHARACTER_ID, 0));
			assertEquals(List.of(), store.loadMemos(CHARACTER_ID));
		}
		catch (Exception e)
		{
			throw new AssertionError("Player auxiliary persistence contract failed", e);
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
