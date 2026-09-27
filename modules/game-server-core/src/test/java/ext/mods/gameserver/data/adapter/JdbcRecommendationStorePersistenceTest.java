package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;

/** Optional PostgreSQL contract test for recommendation persistence. */
class JdbcRecommendationStorePersistenceTest
{
	private static final int GIVER_ID = 2147483007;
	private static final int TARGET_ID = 2147483008;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_recommends WHERE char_id=? OR target_id=?"))
				{
					ps.setInt(1, GIVER_ID);
					ps.setInt(2, TARGET_ID);
					ps.executeUpdate();
				}
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM characters WHERE obj_Id=? OR obj_Id=?"))
				{
					ps.setInt(1, GIVER_ID);
					ps.setInt(2, TARGET_ID);
					ps.executeUpdate();
				}
			}
			catch (Exception ignored)
			{
				// The contract test must not hide its primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void storesRecommendationAndCountersAtomically() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the recommendation persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "RecommendationStoreContractPool");
		seedCharacters();

		final JdbcRecommendationStore store = new JdbcRecommendationStore();
		store.addRecommendation(GIVER_ID, TARGET_ID, 7, 2);

		assertEquals(java.util.List.of(TARGET_ID), store.loadGivenRecommendations(GIVER_ID));
		assertEquals(7, readCounter("rec_have", TARGET_ID));
		assertEquals(2, readCounter("rec_left", GIVER_ID));
	}

	private static void seedCharacters() throws Exception
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM characters WHERE obj_Id=? OR obj_Id=?"))
			{
				ps.setInt(1, GIVER_ID);
				ps.setInt(2, TARGET_ID);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO characters (obj_Id,char_name,rec_have,rec_left) VALUES (?,?,?,?)"))
			{
				ps.setInt(1, GIVER_ID);
				ps.setString(2, "RecommendationGiver");
				ps.setInt(3, 0);
				ps.setInt(4, 9);
				ps.addBatch();
				ps.setInt(1, TARGET_ID);
				ps.setString(2, "RecommendationTarget");
				ps.setInt(3, 0);
				ps.setInt(4, 9);
				ps.addBatch();
				ps.executeBatch();
			}
		}
	}

	private static int readCounter(String column, int objectId) throws Exception
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT " + column + " FROM characters WHERE obj_Id=?"))
		{
			ps.setInt(1, objectId);
			try (var rs = ps.executeQuery())
			{
				assertTrue(rs.next());
				return rs.getInt(1);
			}
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
