package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.CharacterSelection;

/** Optional PostgreSQL contract test for the character-selection read model. */
class JdbcCharacterSelectionStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147482990;
	private static final int WEAPON_OBJECT_ID = 2147482991;
	private static final String ACCOUNT = "character-selection-contract";

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				delete(con, "augmentations", "item_oid", WEAPON_OBJECT_ID);
				delete(con, "items", "owner_id", CHARACTER_ID);
				delete(con, "character_subclasses", "char_obj_id", CHARACTER_ID);
				delete(con, "characters", "obj_Id", CHARACTER_ID);
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void assemblesCharacterSubclassPaperdollAndAugmentationState() throws Exception
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the character-selection contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "CharacterSelectionContractPool");
		seed();

		final CharacterSelection selection = new JdbcCharacterSelectionStore().findByAccount(ACCOUNT).stream().findFirst().orElseThrow();
		assertEquals(CHARACTER_ID, selection.objectId());
		assertEquals("CharacterSelectionContract", selection.name());
		assertEquals(88, selection.classId());
		assertEquals(40, selection.baseClassId());
		assertEquals(80, selection.level());
		assertEquals(8000L, selection.exp());
		assertEquals(900, selection.sp());
		assertEquals(WEAPON_OBJECT_ID, selection.paperdoll()[7][0]);
		assertEquals(16283, selection.augmentationId());
	}

	private static void seed() throws Exception
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			delete(con, "augmentations", "item_oid", WEAPON_OBJECT_ID);
			delete(con, "items", "owner_id", CHARACTER_ID);
			delete(con, "character_subclasses", "char_obj_id", CHARACTER_ID);
			delete(con, "characters", "obj_Id", CHARACTER_ID);
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO characters (account_name,obj_Id,char_name,level,maxHp,curHp,maxMp,curMp,face,hairStyle,hairColor,sex,x,y,z,exp,sp,karma,pvpkills,pkkills,clanid,race,classid,deletetime,accesslevel,lastAccess,base_class) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
			{
				ps.setString(1, ACCOUNT);
				ps.setInt(2, CHARACTER_ID);
				ps.setString(3, "CharacterSelectionContract");
				ps.setInt(4, 79);
				ps.setInt(5, 100);
				ps.setDouble(6, 100);
				ps.setInt(7, 50);
				ps.setDouble(8, 50);
				ps.setInt(9, 0);
				ps.setInt(10, 0);
				ps.setInt(11, 0);
				ps.setInt(12, 0);
				ps.setInt(13, 1);
				ps.setInt(14, 2);
				ps.setInt(15, 3);
				ps.setLong(16, 7000L);
				ps.setInt(17, 700);
				ps.setInt(18, 0);
				ps.setInt(19, 0);
				ps.setInt(20, 0);
				ps.setInt(21, 0);
				ps.setInt(22, 0);
				ps.setInt(23, 88);
				ps.setLong(24, 0L);
				ps.setInt(25, 0);
				ps.setLong(26, 1234L);
				ps.setInt(27, 40);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO character_subclasses (char_obj_id,class_id,exp,sp,level,class_index) VALUES (?,?,?,?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setInt(2, 88);
				ps.setLong(3, 8000L);
				ps.setInt(4, 900);
				ps.setInt(5, 80);
				ps.setInt(6, 1);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO items (owner_id,object_id,item_id,enchant_level,loc,loc_data) VALUES (?,?,?,?,?,?)"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setInt(2, WEAPON_OBJECT_ID);
				ps.setInt(3, 7575);
				ps.setInt(4, 7);
				ps.setString(5, "PAPERDOLL");
				ps.setInt(6, 7);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement("INSERT INTO augmentations (item_oid,attributes,skill_id,skill_level) VALUES (?,?,?,?)"))
			{
				ps.setInt(1, WEAPON_OBJECT_ID);
				ps.setInt(2, 16283);
				ps.setInt(3, 3240);
				ps.setInt(4, 10);
				ps.executeUpdate();
			}
		}
	}

	private static void delete(Connection con, String table, String column, int value) throws Exception
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
