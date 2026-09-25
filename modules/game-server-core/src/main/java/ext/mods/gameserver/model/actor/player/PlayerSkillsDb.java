package ext.mods.gameserver.model.actor.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.commons.jdbc.DatabaseDialect;
import ext.mods.commons.pool.ConnectionPool;
import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigProject;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.data.manager.BufferManager;
import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.enums.skills.SkillType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.records.Timestamp;
import ext.mods.gameserver.skills.AbstractEffect;
import ext.mods.gameserver.skills.L2Skill;
import ext.mods.gameserver.skills.effects.EffectTemplate;

/**
 * character_skills / character_skills_save JDBC for a {@link Player} (att-ver-3.0 onda 2 / A3).
 */
public final class PlayerSkillsDb
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSkillsDb.class);
	
	static final String RESTORE_SKILLS_FOR_CHAR = "SELECT skill_id,skill_level FROM character_skills WHERE char_obj_id=? AND class_index=?";
	static final String RESTORE_SKILLS_FOR_CHAR_ALT_SUBCLASS = "SELECT skill_id,skill_level FROM character_skills WHERE char_obj_id=?";
	static final String DELETE_SKILL_FROM_CHAR = "DELETE FROM character_skills WHERE skill_id=? AND char_obj_id=? AND class_index=?";
	/** Shared with {@link PlayerSubClass#modifySubClass} wipe path (same Connection). */
	public static final String DELETE_CHAR_SKILLS = "DELETE FROM character_skills WHERE char_obj_id=? AND class_index=?";
	
	static final String ADD_SKILL_SAVE = "INSERT INTO character_skills_save (char_obj_id,skill_id,skill_level,effect_count,effect_cur_time,reuse_delay,systime,restore_type,class_index,buff_index,npc) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
	static final String RESTORE_SKILL_SAVE = "SELECT skill_id,skill_level,effect_count,effect_cur_time, reuse_delay, systime, restore_type, npc FROM character_skills_save WHERE char_obj_id=? AND class_index=? ORDER BY buff_index ASC";
	/** Shared with {@link PlayerSubClass#modifySubClass} wipe path (same Connection). */
	public static final String DELETE_SKILL_SAVE = "DELETE FROM character_skills_save WHERE char_obj_id=? AND class_index=?";
	
	private final Player _owner;
	
	public PlayerSkillsDb(Player owner)
	{
		_owner = owner;
	}
	
	/**
	 * Insert or update a skill row. If {@code classIndex > -1}, that index is used; otherwise the active class index.
	 */
	public void storeSkill(L2Skill skill, int classIndex)
	{
		final String addOrUpdateSkill = DatabaseDialect.upsert(
			"character_skills",
			"char_obj_id,skill_id,skill_level,class_index",
			"?,?,?,?",
			"char_obj_id,skill_id,class_index",
			"skill_level");

		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(addOrUpdateSkill))
		{
			ps.setInt(1, _owner.getObjectId());
			ps.setInt(2, skill.getId());
			ps.setInt(3, skill.getLevel());
			ps.setInt(4, (classIndex > -1) ? classIndex : _owner.getClassIndex());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store player skill.", e);
		}
	}
	
	/** Delete one skill row for the active class index. */
	public void deleteSkillFromChar(int skillId)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_SKILL_FROM_CHAR))
		{
			ps.setInt(1, skillId);
			ps.setInt(2, _owner.getObjectId());
			ps.setInt(3, _owner.getClassIndex());
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete player skill.", e);
		}
	}
	
	/** Delete all skill rows for a class index (subclass change / wipe). */
	public void deleteCharSkills(int classIndex)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_CHAR_SKILLS))
		{
			ps.setInt(1, _owner.getObjectId());
			ps.setInt(2, classIndex);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete character skills.", e);
		}
	}
	
	/** Delete effect/reuse save rows for a class index. */
	public void deleteSkillSave(int classIndex)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_SKILL_SAVE))
		{
			ps.setInt(1, _owner.getObjectId());
			ps.setInt(2, classIndex);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete skill save.", e);
		}
	}
	
	/** Restore all skills from database and feed {@link Player#getSkills()}. */
	public void restoreSkills()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(ConfigProject.SUBCLASS_SKILLS ? RESTORE_SKILLS_FOR_CHAR_ALT_SUBCLASS : RESTORE_SKILLS_FOR_CHAR))
		{
			ps.setInt(1, _owner.getObjectId());
			
			if (!ConfigProject.SUBCLASS_SKILLS)
				ps.setInt(2, _owner.getClassIndex());
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					_owner.addSkill(SkillTable.getInstance().getInfo(rs.getInt("skill_id"), rs.getInt("skill_level")), false);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore player skills.", e);
		}
	}
	
	/** Restore skill effects / reuse from character_skills_save, then delete those rows. */
	public void restoreEffects()
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement(RESTORE_SKILL_SAVE))
			{
				ps.setInt(1, _owner.getObjectId());
				ps.setInt(2, _owner.getClassIndex());
				
				try (ResultSet rs = ps.executeQuery())
				{
					while (rs.next())
					{
						final int effectCount = rs.getInt("effect_count");
						final int effectCurTime = rs.getInt("effect_cur_time");
						final long reuseDelay = rs.getLong("reuse_delay");
						final long systime = rs.getLong("systime");
						final int restoreType = rs.getInt("restore_type");
						final boolean npc = rs.getInt("npc") == 1;
						
						final L2Skill skill = SkillTable.getInstance().getInfo(rs.getInt("skill_id"), rs.getInt("skill_level"));
						if (skill == null)
							continue;
						
						final long remainingTime = systime - System.currentTimeMillis();
						if (remainingTime > 10)
						{
							_owner.disableSkill(skill, remainingTime);
							_owner.addTimeStamp(skill, reuseDelay, systime);
						}
						
						if (restoreType > 0)
							continue;
						
						if (skill.hasEffects())
						{
							for (final EffectTemplate template : skill.getEffectTemplates())
							{
								final AbstractEffect effect = template.getEffect(_owner, _owner, skill);
								if (effect != null)
								{
									effect.setCount(effectCount);
									effect.setNpc(npc);
									if (npc)
									{
										var buf = BufferManager.getInstance().getAvailableBuff(skill);
										if (buf != null && buf.time() != 0)
											effect.setPeriod(buf.time());
									}
									effect.setTime(effectCurTime);
									effect.scheduleEffect();
								}
							}
						}
					}
				}
			}
			
			try (PreparedStatement ps = con.prepareStatement(DELETE_SKILL_SAVE))
			{
				ps.setInt(1, _owner.getObjectId());
				ps.setInt(2, _owner.getClassIndex());
				ps.executeUpdate();
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore effects.", e);
		}
	}
	
	/** Persist active effects + reuse timestamps to character_skills_save. */
	public void storeEffect(boolean storeEffects)
	{
		if (!ConfigPlayers.STORE_SKILL_COOLTIME || _owner.isInDuel())
			return;
		
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement(DELETE_SKILL_SAVE))
			{
				ps.setInt(1, _owner.getObjectId());
				ps.setInt(2, _owner.getClassIndex());
				ps.executeUpdate();
			}
			
			int index = 0;
			final List<Integer> storedSkills = new ArrayList<>();
			
			try (PreparedStatement ps = con.prepareStatement(ADD_SKILL_SAVE))
			{
				if (storeEffects)
				{
					for (final AbstractEffect effect : _owner.getAllEffects())
					{
						if (effect.getEffectType() == EffectType.HEAL_OVER_TIME)
							continue;
						
						final L2Skill skill = effect.getSkill();
						if (storedSkills.contains(skill.getReuseHashCode()))
							continue;
						
						storedSkills.add(skill.getReuseHashCode());
						
						if (effect.isHerbEffect() || skill.isToggle() || skill.getSkillType() == SkillType.CONT)
							continue;
						
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, skill.getId());
						ps.setInt(3, skill.getLevel());
						ps.setInt(4, effect.getCount());
						ps.setInt(5, effect.getTime());
						
						final Timestamp timestamp = _owner.getReuseTimeStamp().get(skill.getReuseHashCode());
						if (timestamp != null && timestamp.hasNotPassed())
						{
							ps.setLong(6, timestamp.reuse());
							ps.setDouble(7, timestamp.stamp());
						}
						else
						{
							ps.setLong(6, 0);
							ps.setDouble(7, 0);
						}
						
						ps.setInt(8, 0);
						ps.setInt(9, _owner.getClassIndex());
						ps.setInt(10, ++index);
						ps.setInt(11, effect.isNpc() ? 1 : 0);
						ps.addBatch();
					}
				}
				
				for (final Map.Entry<Integer, Timestamp> entry : _owner.getReuseTimeStamp().entrySet())
				{
					final int hash = entry.getKey();
					if (storedSkills.contains(hash))
						continue;
					
					final Timestamp timestamp = entry.getValue();
					if (timestamp != null && timestamp.hasNotPassed())
					{
						storedSkills.add(hash);
						
						ps.setInt(1, _owner.getObjectId());
						ps.setInt(2, timestamp.skillId());
						ps.setInt(3, timestamp.skillLevel());
						ps.setInt(4, -1);
						ps.setInt(5, -1);
						ps.setLong(6, timestamp.reuse());
						ps.setDouble(7, timestamp.stamp());
						ps.setInt(8, 1);
						ps.setInt(9, _owner.getClassIndex());
						ps.setInt(10, ++index);
						ps.setInt(11, 0);
						ps.addBatch();
					}
				}
				
				ps.executeBatch();
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store player effects.", e);
		}
	}
}
