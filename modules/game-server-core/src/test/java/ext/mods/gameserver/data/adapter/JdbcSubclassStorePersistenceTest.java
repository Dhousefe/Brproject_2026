package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.SubclassRecord;

/** Optional PostgreSQL contract test for subclass persistence. */
class JdbcSubclassStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147482999;
	private static final int CLASS_INDEX = 1;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				deleteByCharacter(con, "character_hennas");
				deleteByCharacter(con, "character_shortcuts");
				deleteByCharacter(con, "character_skills_save");
				deleteByCharacter(con, "character_skills");
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_subclasses WHERE char_obj_id=?"))
				{
					ps.setInt(1, CHARACTER_ID);
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
	void managesSubclassRowsAndWipesIndexedStateAtomically() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the subclass persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "SubclassStoreContractPool");
		final JdbcSubclassStore store = new JdbcSubclassStore();

		store.add(CHARACTER_ID, new SubclassRecord(88, CLASS_INDEX, 1000L, 7, 40));
		assertEquals(List.of(new SubclassRecord(88, CLASS_INDEX, 1000L, 7, 40)), store.load(CHARACTER_ID));

		store.update(CHARACTER_ID, List.of(new SubclassRecord(89, CLASS_INDEX, 2000L, 9, 41)));
		assertEquals(List.of(new SubclassRecord(89, CLASS_INDEX, 2000L, 9, 41)), store.load(CHARACTER_ID));

		seedIndexedState();
		store.wipe(CHARACTER_ID, CLASS_INDEX);
		assertEquals(0, count("character_subclasses"));
		assertEquals(0, count("character_hennas"));
		assertEquals(0, count("character_shortcuts"));
		assertEquals(0, count("character_skills_save"));
		assertEquals(0, count("character_skills"));
	}

	private static void seedIndexedState() throws Exception
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			deleteByCharacter(con, "character_hennas");
			deleteByCharacter(con, "character_shortcuts");
			deleteByCharacter(con, "character_skills_save");
			deleteByCharacter(con, "character_skills");
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_hennas (char_obj_id,slot,class_index) VALUES (?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setInt(2, 0);
				ps.setInt(3, CLASS_INDEX);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_shortcuts (char_obj_id,slot,page,type,id,level,class_index) VALUES (?,?,?,?,?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setInt(2, 0);
				ps.setInt(3, 0);
				ps.setString(4, "ITEM");
				ps.setInt(5, 57);
				ps.setInt(6, 1);
				ps.setInt(7, CLASS_INDEX);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_skills_save (char_obj_id,skill_id,class_index) VALUES (?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setInt(2, 1204);
				ps.setInt(3, CLASS_INDEX);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_skills (char_obj_id,skill_id,class_index) VALUES (?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setInt(2, 1204);
				ps.setInt(3, CLASS_INDEX);
				ps.executeUpdate();
			}
		}
	}

	private static int count(String table) throws Exception
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE char_obj_id=?"))
		{
			ps.setInt(1, CHARACTER_ID);
			try (ResultSet rs = ps.executeQuery())
			{
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	private static void deleteByCharacter(Connection con, String table) throws Exception
	{
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE char_obj_id=?"))
		{
			ps.setInt(1, CHARACTER_ID);
			ps.executeUpdate();
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
