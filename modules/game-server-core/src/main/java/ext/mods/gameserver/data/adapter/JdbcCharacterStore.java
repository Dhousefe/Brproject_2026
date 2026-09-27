package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.CharacterStore;

/** JDBC adapter for character lifecycle persistence. */
public final class JdbcCharacterStore implements CharacterStore
{
	private static final CLogger LOGGER = new CLogger(JdbcCharacterStore.class.getName());

	private static final String SELECT_CLAN = "SELECT clanId FROM characters WHERE obj_id=?";
	private static final String UPDATE_DELETE_TIME = "UPDATE characters SET deletetime=? WHERE obj_id=?";
	private static final String DELETE_CHAR_HENNAS = "DELETE FROM character_hennas WHERE char_obj_id=?";
	private static final String DELETE_CHAR_MACROS = "DELETE FROM character_macroses WHERE char_obj_id=?";
	private static final String DELETE_CHAR_MEMOS = "DELETE FROM character_memo WHERE charId=?";
	private static final String DELETE_CHAR_QUESTS = "DELETE FROM character_quests WHERE charId=?";
	private static final String DELETE_CHAR_RECIPES = "DELETE FROM character_recipebook WHERE charId=?";
	private static final String DELETE_CHAR_RELATIONS = "DELETE FROM character_relations WHERE char_id=? OR friend_id=?";
	private static final String DELETE_CHAR_SHORTCUTS = "DELETE FROM character_shortcuts WHERE char_obj_id=?";
	private static final String DELETE_CHAR_SKILLS = "DELETE FROM character_skills WHERE char_obj_id=?";
	private static final String DELETE_CHAR_SKILLS_SAVE = "DELETE FROM character_skills_save WHERE char_obj_id=?";
	private static final String DELETE_CHAR_SUBCLASSES = "DELETE FROM character_subclasses WHERE char_obj_id=?";
	private static final String DELETE_CHAR_HERO = "DELETE FROM heroes WHERE char_id=?";
	private static final String DELETE_CHAR_NOBLE = "DELETE FROM olympiad_nobles WHERE char_id=?";
	private static final String DELETE_CHAR_SEVEN_SIGNS = "DELETE FROM seven_signs WHERE char_obj_id=?";
	private static final String DELETE_CHAR_PETS = "DELETE FROM pets WHERE item_obj_id IN (SELECT object_id FROM items WHERE items.owner_id=?)";
	private static final String DELETE_CHAR_AUGMENTS = "DELETE FROM augmentations WHERE item_oid IN (SELECT object_id FROM items WHERE items.owner_id=?)";
	private static final String DELETE_CHAR_ITEMS = "DELETE FROM items WHERE owner_id=?";
	private static final String DELETE_CHAR_RBP = "DELETE FROM character_raid_points WHERE char_id=?";
	private static final String DELETE_CHAR = "DELETE FROM characters WHERE obj_Id=?";
	private static final String DELETE_CHAR_CACHE = "DELETE FROM character_data WHERE charId=?";

	@Override
	public int findClanId(int objectId) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SELECT_CLAN))
		{
			ps.setInt(1, objectId);
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	@Override
	public void updateDeleteTime(int objectId, long deleteTime) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_DELETE_TIME))
		{
			ps.setLong(1, deleteTime);
			ps.setInt(2, objectId);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteCharacter(int objectId) throws SQLException
	{
		if (objectId < 0)
			return;

		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				executeDelete(con, DELETE_CHAR_HENNAS, objectId);
				executeDelete(con, DELETE_CHAR_MACROS, objectId);
				executeDelete(con, DELETE_CHAR_MEMOS, objectId);
				executeDelete(con, DELETE_CHAR_QUESTS, objectId);
				executeDelete(con, DELETE_CHAR_RECIPES, objectId);
				executeDeleteRelations(con, objectId);
				executeDelete(con, DELETE_CHAR_SHORTCUTS, objectId);
				executeDelete(con, DELETE_CHAR_SKILLS, objectId);
				executeDelete(con, DELETE_CHAR_SKILLS_SAVE, objectId);
				executeDelete(con, DELETE_CHAR_SUBCLASSES, objectId);
				executeDelete(con, DELETE_CHAR_HERO, objectId);
				executeDelete(con, DELETE_CHAR_NOBLE, objectId);
				executeDelete(con, DELETE_CHAR_SEVEN_SIGNS, objectId);
				executeDelete(con, DELETE_CHAR_PETS, objectId);
				executeDelete(con, DELETE_CHAR_AUGMENTS, objectId);
				executeDelete(con, DELETE_CHAR_ITEMS, objectId);
				executeDelete(con, DELETE_CHAR_RBP, objectId);
				executeDelete(con, DELETE_CHAR, objectId);
				executeDelete(con, DELETE_CHAR_CACHE, objectId);
				con.commit();
			}
			catch (SQLException e)
			{
				try
				{
					con.rollback();
				}
				catch (SQLException rollbackFailure)
				{
					e.addSuppressed(rollbackFailure);
				}
				throw e;
			}
			finally
			{
				con.setAutoCommit(previousAutoCommit);
			}
		}
	}

	private static void executeDelete(Connection con, String sql, int objectId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, objectId);
			ps.executeUpdate();
		}
	}

	private static void executeDeleteRelations(Connection con, int objectId) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(DELETE_CHAR_RELATIONS))
		{
			ps.setInt(1, objectId);
			ps.setInt(2, objectId);
			ps.executeUpdate();
		}
	}
}
