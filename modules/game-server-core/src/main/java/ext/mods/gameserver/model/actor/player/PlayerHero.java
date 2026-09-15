package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.enums.ShortcutType;
import ext.mods.gameserver.enums.actors.MissionType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.serverpackets.ShortCutInit;
import ext.mods.gameserver.network.serverpackets.SkillList;
import ext.mods.gameserver.network.serverpackets.UserInfo;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Hero / noblesse status surface for a {@link Player} (att-ver-3.0 batch).
 */
public final class PlayerHero
{
	private final Player _owner;
	
	private boolean _isNoble;
	private boolean _isHero;
	private long _heroUntil;
	
	public PlayerHero(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isHero()
	{
		return _isHero;
	}
	
	public void setHero(boolean hero)
	{
		if (hero && _owner.getBaseClass() == _owner.getActiveClass())
		{
			for (final L2Skill skill : SkillTable.getHeroSkills())
				_owner.addSkill(skill, false);
		}
		else
		{
			for (final L2Skill skill : SkillTable.getHeroSkills())
				_owner.removeSkill(skill.getId(), false);
		}
		_isHero = hero;
		
		if (!hero)
		{
			boolean sendPacket = false;
			for (var skill : SkillTable.getHeroSkills())
			{
				if (_owner.getSkill(skill.getId()) == null)
				{
					_owner.getShortcutList().deleteShortcuts(skill.getId(), ShortcutType.SKILL);
					sendPacket = true;
				}
			}
			
			if (sendPacket)
				_owner.sendPacket(new ShortCutInit(_owner));
		}
		
		_owner.sendPacket(new SkillList(_owner));
	}
	
	public boolean isNoble()
	{
		return _isNoble;
	}
	
	public void setNoble(boolean isNoble, boolean storeInDb)
	{
		if (isNoble)
		{
			for (final L2Skill skill : SkillTable.getNobleSkills())
				_owner.addSkill(skill, false);
		}
		else
		{
			for (final L2Skill skill : SkillTable.getNobleSkills())
				_owner.removeSkill(skill.getId(), false);
		}
		
		_isNoble = isNoble;
		
		_owner.sendPacket(new SkillList(_owner));
		_owner.sendPacket(new UserInfo(_owner));
		
		if (storeInDb)
			_owner.getPersistence().storeNobless(isNoble);
		
		if (isNoble && storeInDb)
			_owner.getMissions().update(MissionType.NOBLE);
	}
	
	public void setHeroUntil(long val)
	{
		_heroUntil = val;
	}
	
	public long getHeroUntil()
	{
		return _heroUntil;
	}
}
