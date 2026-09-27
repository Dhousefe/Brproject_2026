package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.SkillRecord;
import ext.mods.gameserver.data.repository.SkillSaveRecord;

/** Optional PostgreSQL contract test for skill persistence. */
class JdbcSkillStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147482997;
	private static final int CLASS_INDEX = 0;

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				deleteByCharacter(con, "character_skills_save");
				deleteByCharacter(con, "character_skills");
			}
			catch (Exception ignored)
			{
				// Cleanup must not hide the primary assertion failure.
			}
		}
		ConnectionPool.shutdown();
	}

	@Test
	void managesSkillsAndSavedEffects()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the skill persistence contract test");
		try
		{
			ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "SkillStoreContractPool");
			final JdbcSkillStore store = new JdbcSkillStore();

			store.upsertSkill(CHARACTER_ID, 99, 1, CLASS_INDEX);
			store.upsertSkill(CHARACTER_ID, 99, 2, CLASS_INDEX);
			assertEquals(List.of(new SkillRecord(99, 2)), store.loadSkills(CHARACTER_ID, CLASS_INDEX, false));

			final SkillSaveRecord save = new SkillSaveRecord(99, 2, 3, 40, 5000L, System.currentTimeMillis() + 5000L, 0, 1, true);
			store.replaceSkillSaves(CHARACTER_ID, CLASS_INDEX, List.of(save));
			assertEquals(List.of(save), store.loadSkillSaves(CHARACTER_ID, CLASS_INDEX));

			store.deleteSkill(CHARACTER_ID, 99, CLASS_INDEX);
			store.deleteSkillSaves(CHARACTER_ID, CLASS_INDEX);
			assertEquals(List.of(), store.loadSkills(CHARACTER_ID, CLASS_INDEX, false));
			assertEquals(List.of(), store.loadSkillSaves(CHARACTER_ID, CLASS_INDEX));
		}
		catch (Exception e)
		{
			throw new AssertionError("Skill persistence contract failed", e);
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
