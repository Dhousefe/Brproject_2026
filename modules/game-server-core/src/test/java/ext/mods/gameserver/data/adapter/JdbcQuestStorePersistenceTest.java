package ext.mods.gameserver.data.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.QuestStore;

/** Optional PostgreSQL contract test for player quest persistence. */
class JdbcQuestStorePersistenceTest
{
	private static final int CHARACTER_ID = 2147483006;
	private static final String QUEST_NAME = "__contract_quest_state__";

	@AfterEach
	void tearDown()
	{
		if (databaseUrl() != null)
		{
			try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("DELETE FROM character_quests WHERE charId = ? AND name = ?"))
			{
				ps.setInt(1, CHARACTER_ID);
				ps.setString(2, QUEST_NAME);
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
	void savesLoadsAndCompletesQuestStateThroughTheAdapter()
	{
		Assumptions.assumeTrue(databaseUrl() != null, "Set DB_TEST_URL to run the quest persistence contract test");
		ConnectionPool.init(databaseUrl(), databaseUser(), databasePassword(), "QuestStoreContractPool");

		final JdbcQuestStore store = new JdbcQuestStore();
		store.saveVariable(CHARACTER_ID, QUEST_NAME, "<state>", "STARTED");
		store.saveVariable(CHARACTER_ID, QUEST_NAME, "cond", "2");

		final QuestStore.VariableRecord condition = store.loadVariables(CHARACTER_ID).stream().filter(value -> value.questName().equals(QUEST_NAME) && value.variable().equals("cond")).findFirst().orElseThrow();
		assertEquals("2", condition.value());

		store.deleteVariable(CHARACTER_ID, QUEST_NAME, "cond");
		assertTrue(store.loadVariables(CHARACTER_ID).stream().noneMatch(value -> value.questName().equals(QUEST_NAME) && value.variable().equals("cond")));
		store.completeQuest(CHARACTER_ID, QUEST_NAME);
		assertTrue(store.loadVariables(CHARACTER_ID).stream().anyMatch(value -> value.questName().equals(QUEST_NAME) && value.variable().equals("<state>")));
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
