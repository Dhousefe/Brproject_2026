package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.QuestStore;

/** JDBC adapter for player quest state persistence. */
public final class JdbcQuestStore implements QuestStore
{
	private static final CLogger LOGGER = new CLogger(JdbcQuestStore.class.getName());

	private static final String LOAD_VARIABLES = "SELECT name,var,value FROM character_quests WHERE charId=?";
	private static final String DELETE_VARIABLE = "DELETE FROM character_quests WHERE charId=? AND name=? AND var=?";
	private static final String DELETE_QUEST = "DELETE FROM character_quests WHERE charId=? AND name=?";
	private static final String COMPLETE_QUEST = "DELETE FROM character_quests WHERE charId=? AND name=? AND var<>'<state>'";

	private static String saveVariableSql()
	{
		return DatabaseDialect.upsert("character_quests", "charId,name,var,value", "?,?,?,?", "charId,name,var", "value");
	}

	@Override
	public List<VariableRecord> loadVariables(int characterId)
	{
		final List<VariableRecord> variables = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_VARIABLES))
		{
			ps.setInt(1, characterId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					variables.add(new VariableRecord(rs.getString("name"), rs.getString("var"), rs.getString("value")));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to load quests for character " + characterId + ".", e);
		}
		return variables;
	}

	@Override
	public void saveVariable(int characterId, String questName, String variable, String value)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(saveVariableSql()))
		{
			ps.setInt(1, characterId);
			ps.setString(2, questName);
			ps.setString(3, variable);
			ps.setString(4, value);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to save quest variable for character " + characterId + ".", e);
		}
	}

	@Override
	public void deleteVariable(int characterId, String questName, String variable)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_VARIABLE))
		{
			ps.setInt(1, characterId);
			ps.setString(2, questName);
			ps.setString(3, variable);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to delete quest variable for character " + characterId + ".", e);
		}
	}

	@Override
	public void deleteQuest(int characterId, String questName)
	{
		executeDelete(DELETE_QUEST, characterId, questName);
	}

	@Override
	public void completeQuest(int characterId, String questName)
	{
		executeDelete(COMPLETE_QUEST, characterId, questName);
	}

	private void executeDelete(String sql, int characterId, String questName)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, characterId);
			ps.setString(2, questName);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to delete quest state for character " + characterId + ".", e);
		}
	}
}
