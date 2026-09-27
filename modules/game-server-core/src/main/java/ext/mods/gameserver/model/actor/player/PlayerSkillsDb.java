package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigProject;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.data.manager.BufferManager;
import ext.mods.gameserver.data.repository.SkillSaveRecord;
import ext.mods.gameserver.data.service.SkillPersistenceService;
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
		try
		{
			SkillPersistenceService.upsertSkill(_owner.getObjectId(), skill.getId(), skill.getLevel(), (classIndex > -1) ? classIndex : _owner.getClassIndex());
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store player skill.", e);
		}
	}
	
	/** Delete one skill row for the active class index. */
	public void deleteSkillFromChar(int skillId)
	{
		try
		{
			SkillPersistenceService.deleteSkill(_owner.getObjectId(), skillId, _owner.getClassIndex());
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete player skill.", e);
		}
	}
	
	/** Delete all skill rows for a class index (subclass change / wipe). */
	public void deleteCharSkills(int classIndex)
	{
		try
		{
			SkillPersistenceService.deleteSkills(_owner.getObjectId(), classIndex);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete character skills.", e);
		}
	}
	
	/** Delete effect/reuse save rows for a class index. */
	public void deleteSkillSave(int classIndex)
	{
		try
		{
			SkillPersistenceService.deleteSkillSaves(_owner.getObjectId(), classIndex);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete skill save.", e);
		}
	}
	
	/** Restore all skills from database and feed {@link Player#getSkills()}. */
	public void restoreSkills()
	{
		try
		{
			for (var skill : SkillPersistenceService.loadSkills(_owner.getObjectId(), _owner.getClassIndex(), ConfigProject.SUBCLASS_SKILLS))
			{
				_owner.addSkill(SkillTable.getInstance().getInfo(skill.skillId(), skill.skillLevel()), false);
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
		try
		{
			for (SkillSaveRecord record : SkillPersistenceService.loadSkillSaves(_owner.getObjectId(), _owner.getClassIndex()))
			{
				final L2Skill skill = SkillTable.getInstance().getInfo(record.skillId(), record.skillLevel());
				if (skill == null)
					continue;

				final long remainingTime = record.systemTime() - System.currentTimeMillis();
				if (remainingTime > 10)
				{
					_owner.disableSkill(skill, remainingTime);
					_owner.addTimeStamp(skill, record.reuseDelay(), record.systemTime());
				}

				if (record.restoreType() > 0 || !skill.hasEffects())
					continue;

				for (final EffectTemplate template : skill.getEffectTemplates())
				{
					final AbstractEffect effect = template.getEffect(_owner, _owner, skill);
					if (effect == null)
						continue;
					effect.setCount(record.effectCount());
					effect.setNpc(record.npc());
					if (record.npc())
					{
						var buf = BufferManager.getInstance().getAvailableBuff(skill);
						if (buf != null && buf.time() != 0)
							effect.setPeriod(buf.time());
					}
					effect.setTime(record.effectCurrentTime());
					effect.scheduleEffect();
				}
			}
			SkillPersistenceService.deleteSkillSaves(_owner.getObjectId(), _owner.getClassIndex());
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
		
		try
		{
			int index = 0;
			final List<Integer> storedSkills = new ArrayList<>();
			final List<SkillSaveRecord> records = new ArrayList<>();

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

					final Timestamp timestamp = _owner.getReuseTimeStamp().get(skill.getReuseHashCode());
					records.add(new SkillSaveRecord(skill.getId(), skill.getLevel(), effect.getCount(), effect.getTime(), timestamp != null && timestamp.hasNotPassed() ? timestamp.reuse() : 0, timestamp != null && timestamp.hasNotPassed() ? timestamp.stamp() : 0, 0, ++index, effect.isNpc()));
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
					records.add(new SkillSaveRecord(timestamp.skillId(), timestamp.skillLevel(), -1, -1, timestamp.reuse(), timestamp.stamp(), 1, ++index, false));
				}
			}
			SkillPersistenceService.replaceSkillSaves(_owner.getObjectId(), _owner.getClassIndex(), records);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store player effects.", e);
		}
	}
}
