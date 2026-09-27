package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.SubclassRecord;
import ext.mods.gameserver.data.repository.SubclassStore;

/** JDBC adapter for player subclass persistence. */
public final class JdbcSubclassStore implements SubclassStore
{
	private static final String LOAD = "SELECT class_id,exp,sp,level,class_index FROM character_subclasses WHERE char_obj_id=? ORDER BY class_index ASC";
	private static final String ADD = "INSERT INTO character_subclasses (char_obj_id,class_id,exp,sp,level,class_index) VALUES (?,?,?,?,?,?)";
	private static final String UPDATE = "UPDATE character_subclasses SET exp=?,sp=?,level=?,class_id=? WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_SUBCLASS = "DELETE FROM character_subclasses WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_HENNAS = "DELETE FROM character_hennas WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_SHORTCUTS = "DELETE FROM character_shortcuts WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_SKILL_SAVE = "DELETE FROM character_skills_save WHERE char_obj_id=? AND class_index=?";
	private static final String DELETE_SKILLS = "DELETE FROM character_skills WHERE char_obj_id=? AND class_index=?";

	@Override
	public List<SubclassRecord> load(int characterObjectId) throws SQLException
	{
		final List<SubclassRecord> subclasses = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD))
		{
			ps.setInt(1, characterObjectId);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					subclasses.add(new SubclassRecord(rs.getInt("class_id"), rs.getInt("class_index"), rs.getLong("exp"), rs.getInt("sp"), rs.getInt("level")));
			}
		}
		return subclasses;
	}

	@Override
	public void update(int characterObjectId, Collection<SubclassRecord> subclasses) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE))
		{
			for (SubclassRecord subclass : subclasses)
			{
				ps.setLong(1, subclass.exp());
				ps.setInt(2, subclass.sp());
				ps.setInt(3, subclass.level());
				ps.setInt(4, subclass.classId());
				ps.setInt(5, characterObjectId);
				ps.setInt(6, subclass.classIndex());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	@Override
	public void add(int characterObjectId, SubclassRecord subclass) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(ADD))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, subclass.classId());
			ps.setLong(3, subclass.exp());
			ps.setInt(4, subclass.sp());
			ps.setInt(5, subclass.level());
			ps.setInt(6, subclass.classIndex());
			ps.executeUpdate();
		}
	}

	@Override
	public void wipe(int characterObjectId, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				executeDelete(con, DELETE_HENNAS, characterObjectId, classIndex);
				executeDelete(con, DELETE_SHORTCUTS, characterObjectId, classIndex);
				executeDelete(con, DELETE_SKILL_SAVE, characterObjectId, classIndex);
				executeDelete(con, DELETE_SKILLS, characterObjectId, classIndex);
				executeDelete(con, DELETE_SUBCLASS, characterObjectId, classIndex);
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

	private static void executeDelete(Connection con, String sql, int characterObjectId, int classIndex) throws SQLException
	{
		try (PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, classIndex);
			ps.executeUpdate();
		}
	}
}
