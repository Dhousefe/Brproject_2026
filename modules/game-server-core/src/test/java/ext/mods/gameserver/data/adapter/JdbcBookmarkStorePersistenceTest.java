package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.model.records.Bookmark;

/** Optional PostgreSQL/MariaDB contract test for player bookmark persistence. */
class JdbcBookmarkStorePersistenceTest
{
	private static final String NAME = "bookmark-contract";
	private static final int OBJECT_ID = 2147482992;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM bookmarks WHERE name=? AND obj_Id=?"))
			{
				ps.setString(1, NAME);
				ps.setInt(2, OBJECT_ID);
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
	void savesLoadsAndDeletesBookmark() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the bookmark contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "BookmarkContractPool");
		final JdbcBookmarkStore store = new JdbcBookmarkStore();
		final Bookmark bookmark = new Bookmark(NAME, OBJECT_ID, 100, 200, 300);

		store.save(bookmark);
		assertTrue(store.load().contains(bookmark));
		assertEquals(bookmark, store.load().stream().filter(bookmark::equals).findFirst().orElseThrow());

		store.delete(NAME, OBJECT_ID);
		assertTrue(store.load().stream().noneMatch(bookmark::equals));
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
