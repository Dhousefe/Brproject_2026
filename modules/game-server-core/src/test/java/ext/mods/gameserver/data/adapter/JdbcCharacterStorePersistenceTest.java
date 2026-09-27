package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;

/** Optional PostgreSQL contract test for atomic character lifecycle persistence. */
class JdbcCharacterStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147483005;
	private static final int ITEM_OBJECT_ID = 2147483006;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				deleteByColumn(con, "augmentations", "item_oid", ITEM_OBJECT_ID);
				deleteByColumn(con, "pets", "item_obj_id", ITEM_OBJECT_ID);
				deleteByColumn(con, "items", "owner_id", CHARACTER_ID);
				deleteByColumn(con, "character_hennas", "char_obj_id", CHARACTER_ID);
				deleteByColumn(con, "character_macroses", "char_obj_id", CHARACTER_ID);
				deleteByColumn(con, "character_memo", "charId", CHARACTER_ID);
				deleteByColumn(con, "character_quests", "charId", CHARACTER_ID);
				deleteByColumn(con, "character_recipebook", "charId", CHARACTER_ID);
				deleteByColumn(con, "character_shortcuts", "char_obj_id", CHARACTER_ID);
				deleteByColumn(con, "character_skills", "char_obj_id", CHARACTER_ID);
				deleteByColumn(con, "character_skills_save", "char_obj_id", CHARACTER_ID);
				deleteByColumn(con, "character_subclasses", "char_obj_id", CHARACTER_ID);
				deleteByColumn(con, "character_raid_points", "char_id", CHARACTER_ID);
				try (PreparedStatement ps = con.prepareStatement("DELETE FROM character_relations WHERE char_id=? OR friend_id=?"))
				{
					ps.setInt(1, CHARACTER_ID);
					ps.setInt(2, CHARACTER_ID);
					ps.executeUpdate();
				}
				deleteByColumn(con, "characters", "obj_Id", CHARACTER_ID);
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void managesCharacterLifecycleAndDeletesOwnedRowsAtomically() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the character persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "CharacterStoreContractPool");
		seedCharacter();

		final JdbcCharacterStore store = new JdbcCharacterStore();
		assertEquals(42, store.findClanId(CHARACTER_ID));

		store.updateDeleteTime(CHARACTER_ID, 123456789L);
		assertEquals(123456789L, readDeleteTime());

		store.deleteCharacter(CHARACTER_ID);
		assertEquals(0, count("characters", "obj_Id", CHARACTER_ID));
		assertEquals(0, count("items", "owner_id", CHARACTER_ID));
		assertEquals(0, count("augmentations", "item_oid", ITEM_OBJECT_ID));
		assertEquals(0, count("pets", "item_obj_id", ITEM_OBJECT_ID));
		assertEquals(0, count("character_macroses", "char_obj_id", CHARACTER_ID));
		assertEquals(0, count("character_skills", "char_obj_id", CHARACTER_ID));
	}

	private void seedCharacter() throws Exception
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			deleteByColumn(con, "characters", "obj_Id", CHARACTER_ID);
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO characters (obj_Id,char_name,clanid,deletetime) VALUES (?,?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setString(2, "CharacterStoreContract");
				ps.setInt(3, 42);
				ps.setLong(4, 0L);
				ps.executeUpdate();
			}
			insert(con, "INSERT INTO character_macroses (char_obj_id,id) VALUES (?,?)", CHARACTER_ID, 1);
			insert(con, "INSERT INTO character_skills (char_obj_id,skill_id,class_index) VALUES (?,?,?)", CHARACTER_ID, 1204, 0);
			insert(con, "INSERT INTO items (owner_id,object_id,item_id) VALUES (?,?,?)", CHARACTER_ID, ITEM_OBJECT_ID, 57);
			insert(con, "INSERT INTO augmentations (item_oid,attributes,skill_id,skill_level) VALUES (?,?,?,?)", ITEM_OBJECT_ID, 1, 3240, 10);
			insert(con, "INSERT INTO pets (item_obj_id) VALUES (?)", ITEM_OBJECT_ID);
		}
	}

	private long readDeleteTime()
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT deletetime FROM characters WHERE obj_Id=?"))
		{
			ps.setInt(1, CHARACTER_ID);
			try (ResultSet rs = ps.executeQuery())
			{
				if (!rs.next())
					throw new AssertionError("Contract character was not stored");
				return rs.getLong(1);
			}
		}
		catch (Exception e)
		{
			throw new AssertionError("Could not read contract character", e);
		}
	}

	private int count(String table, String column, int value)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?"))
		{
			ps.setInt(1, value);
			try (ResultSet rs = ps.executeQuery())
			{
				rs.next();
				return rs.getInt(1);
			}
		}
		catch (Exception e)
		{
			throw new AssertionError("Could not count contract rows", e);
		}
	}

	private static void insert(Connection con, String sql, int... values) throws Exception
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			for (int i = 0; i < values.length; i++)
				ps.setInt(i + 1, values[i]);
			ps.executeUpdate();
		}
	}

	private static void deleteByColumn(Connection con, String table, String column, int value) throws Exception
	{
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table + " WHERE " + column + "=?"))
		{
			ps.setInt(1, value);
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
