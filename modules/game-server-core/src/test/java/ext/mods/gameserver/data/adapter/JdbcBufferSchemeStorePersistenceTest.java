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
import ext.mods.gameserver.data.repository.BufferSchemeStore.SchemeRecord;

/** Optional PostgreSQL contract test for NPC buffer scheme persistence. */
class JdbcBufferSchemeStorePersistenceTest
{
	private static final int PLAYER_ID = 2147483004;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM buffer_schemes WHERE object_id = ?"))
			{
				ps.setInt(1, PLAYER_ID);
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
	void replacesAndLoadsSchemesThroughTheAdapter()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the buffer scheme persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "BufferSchemeStoreContractPool");

		final JdbcBufferSchemeStore store = new JdbcBufferSchemeStore();
		store.replaceSchemes(List.of(new SchemeRecord(PLAYER_ID, "Fighter", "1204,1086", "2,1"), new SchemeRecord(PLAYER_ID, "Mage", "1040", "3")));

		assertEquals(2, store.loadSchemes().stream().filter(scheme -> scheme.playerId() == PLAYER_ID).count());
		assertTrue(store.loadSchemes().stream().anyMatch(scheme -> scheme.playerId() == PLAYER_ID && scheme.name().equals("Fighter") && scheme.skills().equals("1204,1086")));

		store.replaceSchemes(List.of(new SchemeRecord(PLAYER_ID, "Fighter", "1204", "2")));
		final List<SchemeRecord> loaded = store.loadSchemes().stream().filter(scheme -> scheme.playerId() == PLAYER_ID).toList();
		assertEquals(1, loaded.size());
		assertEquals("1204", loaded.get(0).skills());
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
