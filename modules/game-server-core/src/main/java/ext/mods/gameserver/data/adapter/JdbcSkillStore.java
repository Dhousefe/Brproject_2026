package ext.mods.gameserver.data.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.gameserver.data.repository.SkillRecord;
import ext.mods.gameserver.data.repository.SkillSaveRecord;
import ext.mods.gameserver.data.repository.SkillStore;

/** JDBC adapter for character skills and saved skill effects. */
public final class JdbcSkillStore implements SkillStore
{
	private static final String LOAD_SKILLS = "SELECT skill_id,skill_level FROM character_skills WHERE char_obj_id=? AND class_index=?";
	private static final String LOAD_ALL_SKILLS = "SELECT skill_id,skill_level FROM character_skills WHERE char_obj_id=?";
	private static final String UPSERT_SKILL = DatabaseDialect.upsert("character_skills", "char_obj_id,skill_id,skill_level,class_index", "?,?,?,?", "char_obj_id,skill_id,class_index", "skill_level");
	private static final String DELETE_SKILL = "DELETE FROM character_skills WHERE skill_id=? AND char_obj_id=? AND class_index=?";
	private static final String DELETE_SKILLS = "DELETE FROM character_skills WHERE char_obj_id=? AND class_index=?";
	private static final String LOAD_SKILL_SAVES = "SELECT skill_id,skill_level,effect_count,effect_cur_time,reuse_delay,systime,restore_type,npc,buff_index FROM character_skills_save WHERE char_obj_id=? AND class_index=? ORDER BY buff_index ASC";
	private static final String DELETE_SKILL_SAVES = "DELETE FROM character_skills_save WHERE char_obj_id=? AND class_index=?";
	private static final String INSERT_SKILL_SAVE = "INSERT INTO character_skills_save (char_obj_id,skill_id,skill_level,effect_count,effect_cur_time,reuse_delay,systime,restore_type,class_index,buff_index,npc) VALUES (?,?,?,?,?,?,?,?,?,?,?)";

	@Override
	public List<SkillRecord> loadSkills(int characterObjectId, int classIndex, boolean allClassIndexes) throws SQLException
	{
		final List<SkillRecord> skills = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(allClassIndexes ? LOAD_ALL_SKILLS : LOAD_SKILLS))
		{
			ps.setInt(1, characterObjectId);
			if (!allClassIndexes)
				ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					skills.add(new SkillRecord(rs.getInt("skill_id"), rs.getInt("skill_level")));
			}
		}
		return skills;
	}

	@Override
	public void upsertSkill(int characterObjectId, int skillId, int skillLevel, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(UPSERT_SKILL))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, skillId);
			ps.setInt(3, skillLevel);
			ps.setInt(4, classIndex);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteSkill(int characterObjectId, int skillId, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_SKILL))
		{
			ps.setInt(1, skillId);
			ps.setInt(2, characterObjectId);
			ps.setInt(3, classIndex);
			ps.executeUpdate();
		}
	}

	@Override
	public void deleteSkills(int characterObjectId, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_SKILLS))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, classIndex);
			ps.executeUpdate();
		}
	}

	@Override
	public List<SkillSaveRecord> loadSkillSaves(int characterObjectId, int classIndex) throws SQLException
	{
		final List<SkillSaveRecord> records = new ArrayList<>();
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_SKILL_SAVES))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, classIndex);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					records.add(new SkillSaveRecord(rs.getInt("skill_id"), rs.getInt("skill_level"), rs.getInt("effect_count"), rs.getInt("effect_cur_time"), rs.getLong("reuse_delay"), rs.getLong("systime"), rs.getInt("restore_type"), rs.getInt("buff_index"), rs.getInt("npc") == 1));
			}
		}
		return records;
	}

	@Override
	public void deleteSkillSaves(int characterObjectId, int classIndex) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(DELETE_SKILL_SAVES))
		{
			ps.setInt(1, characterObjectId);
			ps.setInt(2, classIndex);
			ps.executeUpdate();
		}
	}

	@Override
	public void replaceSkillSaves(int characterObjectId, int classIndex, Collection<SkillSaveRecord> records) throws SQLException
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			final boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try
			{
				try (PreparedStatement delete = con.prepareStatement(DELETE_SKILL_SAVES))
				{
					delete.setInt(1, characterObjectId);
					delete.setInt(2, classIndex);
					delete.executeUpdate();
				}

				try (PreparedStatement insert = con.prepareStatement(INSERT_SKILL_SAVE))
				{
					for (SkillSaveRecord record : records)
					{
						insert.setInt(1, characterObjectId);
						insert.setInt(2, record.skillId());
						insert.setInt(3, record.skillLevel());
						insert.setInt(4, record.effectCount());
						insert.setInt(5, record.effectCurrentTime());
						insert.setLong(6, record.reuseDelay());
						insert.setLong(7, record.systemTime());
						insert.setInt(8, record.restoreType());
						insert.setInt(9, classIndex);
						insert.setInt(10, record.buffIndex());
						insert.setInt(11, record.npc() ? 1 : 0);
						insert.addBatch();
					}
					insert.executeBatch();
				}
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
}
