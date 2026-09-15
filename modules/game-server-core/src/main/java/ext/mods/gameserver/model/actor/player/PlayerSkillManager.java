/*
 * Copyleft © 2024-2026 L2Brproject
 * * This file is part of L2Brproject derived from aCis409/RusaCis3.8
 * * L2Brproject is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License.
 * * L2Brproject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
 * Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY,
 * SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
 * as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigSiege;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.enums.ShortcutType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.holder.skillnode.GeneralSkillNode;
import ext.mods.gameserver.model.records.Timestamp;
import ext.mods.gameserver.network.serverpackets.ShortBuffStatusUpdate;
import ext.mods.gameserver.network.serverpackets.SkillCoolTime;
import ext.mods.gameserver.network.serverpackets.SkillList;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Skill-related operations extracted from {@link Player} (att-ver-3.0).
 * <p>
 * Manages the player's {@link L2Skill} collection, the per-skill reuse timestamps,
 * the short-buff status task, and the public API used by trainers, subclasses and
 * hot-path callers (addSkill / removeSkill / getReuseTimeStamp, etc.).
 * </p>
 * <p>
 * Persistence (character_skills / character_skills_save JDBC) remains in {@link PlayerSkillsDb}.
 * </p>
 */
public final class PlayerSkillManager
{
	/** Sort skill nodes by required minimum level (ascending). */
	private static final Comparator<GeneralSkillNode> COMPARE_SKILLS_BY_MIN_LVL = Comparator.comparing(GeneralSkillNode::getMinLvl);
	/** Sort skill nodes by level / value (ascending). */
	private static final Comparator<GeneralSkillNode> COMPARE_SKILLS_BY_LVL = Comparator.comparing(GeneralSkillNode::getValue);

	private final Player _owner;

	private final PlayerSkillsDb _skillsDb;

	/** Active skills owned by the player, keyed by skill id. */
	private final Map<Integer, L2Skill> _skills = new ConcurrentSkipListMap<>();

	/** Reuse timestamps keyed by skill reuse-hash-code. */
	private final Map<Integer, Timestamp> _reuseTimeStamps = new ConcurrentHashMap<>();

	private ScheduledFuture<?> _shortBuffTask;
	private int _shortBuffTaskSkillId;

	public PlayerSkillManager(Player owner)
	{
		_owner = owner;
		_skillsDb = owner.getSkillsDb();
	}

	public Map<Integer, L2Skill> getSkills()
	{
		return _skills;
	}

	public Collection<Timestamp> getReuseTimeStamps()
	{
		return _reuseTimeStamps.values();
	}

	public Map<Integer, Timestamp> getReuseTimeStamp()
	{
		return _reuseTimeStamps;
	}

	public int getShortBuffTaskSkillId()
	{
		return _shortBuffTaskSkillId;
	}

	public void setShortBuffTaskSkillId(int id)
	{
		_shortBuffTaskSkillId = id;
	}

	// --------------------------------------------------------------------
	// Skill lifecycle
	// --------------------------------------------------------------------

	/**
	 * Add a {@link L2Skill} and its Func objects to the calculator set of the {@link Player}. Don't refresh shortcuts.
	 * @see Player#addSkill(L2Skill, boolean, boolean)
	 * @param newSkill : The skill to add.
	 * @param store : If true, we save the skill on database.
	 * @return true if the skill has been successfully added.
	 */
	public boolean addSkill(L2Skill newSkill, boolean store)
	{
		return addSkill(newSkill, store, false);
	}

	/**
	 * Add a {@link L2Skill} and its Func objects to the calculator set of the {@link Player}.<BR>
	 * <ul>
	 * <li>Replace or add oldSkill by newSkill (only if oldSkill is different than newSkill)</li>
	 * <li>If an old skill has been replaced, remove all its Func objects of Creature calculator set</li>
	 * <li>Add Func objects of newSkill to the calculator set of the Creature</li>
	 * </ul>
	 * @param newSkill : The skill to add.
	 * @param store : If true, we save the skill on database.
	 * @param updateShortcuts : If true, we refresh all shortcuts associated to that skill (should be only called when skill upgrades, either manually or by trainer).
	 * @return true if the skill has been successfully added.
	 */
	public boolean addSkill(L2Skill newSkill, boolean store, boolean updateShortcuts)
	{
		if (newSkill == null)
			return false;

		final L2Skill oldSkill = _skills.get(newSkill.getId());
		if (oldSkill != null && oldSkill.equals(newSkill))
			return false;

		_skills.put(newSkill.getId(), newSkill);

		if (oldSkill != null)
		{
			if (oldSkill.triggerAnotherSkill())
				removeSkill(oldSkill.getTriggeredId(), false);

			_owner.removeStatsByOwner(oldSkill);
		}

		_owner.addStatFuncs(newSkill.getStatFuncs(_owner));

		if (oldSkill != null && _owner.getChanceSkills() != null)
			_owner.removeChanceSkill(oldSkill.getId());

		if (newSkill.isChance())
			_owner.addChanceTrigger(newSkill);

		if (store)
			storeSkill(newSkill, -1);

		if (updateShortcuts || ConfigPlayers.AUTO_LEARN_SKILLS)
			_owner.getShortcutList().refreshShortcuts(s -> s.getId() == newSkill.getId() && s.getType() == ShortcutType.SKILL, newSkill.getLevel());

		return true;
	}

	/**
	 * Remove a {@link L2Skill} from this {@link Player}. If parameter store is true, we also remove it from database and update shortcuts.
	 * @param skillId : The skill identifier to remove.
	 * @param store : If true, we delete the skill from database.
	 * @return the L2Skill removed or null if it couldn't be removed.
	 */
	public L2Skill removeSkill(int skillId, boolean store)
	{
		return removeSkill(skillId, store, true);
	}

	/**
	 * Remove a {@link L2Skill} from this {@link Player}. If parameter store is true, we also remove it from database and update shortcuts.
	 * @param skillId : The skill identifier to remove.
	 * @param store : If true, we delete the skill from database.
	 * @param removeEffect : If true, we remove the associated effect if existing.
	 * @return the L2Skill removed or null if it couldn't be removed.
	 */
	public L2Skill removeSkill(int skillId, boolean store, boolean removeEffect)
	{
		final L2Skill oldSkill = _skills.remove(skillId);
		if (oldSkill == null)
			return null;

		if (oldSkill.triggerAnotherSkill() && oldSkill.getTriggeredId() > 0)
			removeSkill(oldSkill.getTriggeredId(), false);

		if (_owner.getCast().getCurrentSkill() != null && skillId == _owner.getCast().getCurrentSkill().getId())
			_owner.getCast().stop();

		if (removeEffect)
		{
			_owner.removeStatsByOwner(oldSkill);
			_owner.stopSkillEffects(skillId);
		}

		if (oldSkill.isChance() && _owner.getChanceSkills() != null)
			_owner.removeChanceSkill(skillId);

		if (store)
		{
			_skillsDb.deleteSkillFromChar(skillId);

			if (!oldSkill.isPassive())
				_owner.getShortcutList().deleteShortcuts(skillId, ShortcutType.SKILL);
		}
		return oldSkill;
	}

	/**
	 * Insert or update a {@link Player} skill in the database.<br>
	 * If newClassIndex > -1, the skill will be stored with that class index, not the current one.
	 * @param skill : The skill to add or update (if updated, only the level is refreshed).
	 * @param classIndex : The current class index to set, or current if none is found.
	 */
	public void storeSkill(L2Skill skill, int classIndex)
	{
		_skillsDb.storeSkill(skill, classIndex);
	}

	/**
	 * Restore all skills from database for this {@link Player} and feed getSkills().
	 */
	public void restoreSkills()
	{
		_skillsDb.restoreSkills();
	}

	/**
	 * Restore {@link L2Skill} effects of this {@link Player} from the database.
	 */
	public void restoreEffects()
	{
		_skillsDb.restoreEffects();
	}

	/**
	 * Persist active effects + reuse timestamps to character_skills_save.
	 */
	public void storeEffect(boolean storeEffects)
	{
		_skillsDb.storeEffect(storeEffects);
	}

	/**
	 * Build & send the {@link SkillCoolTime} packet to the owner (used at login).
	 */
	public void sendSkillCoolTime()
	{
		_owner.sendPacket(new SkillCoolTime(_owner));
	}

	// --------------------------------------------------------------------
	// Skill learning / class changes
	// --------------------------------------------------------------------

	public void giveSkills()
	{
		if (ConfigPlayers.AUTO_LEARN_SKILLS && _owner.getStatus().getLevel() <= ConfigPlayers.LVL_AUTO_LEARN_SKILLS)
			rewardSkills();
		else
		{
			for (final GeneralSkillNode skill : getAvailableAutoGetSkills())
				addSkill(skill.getSkill(), false);

			if (_owner.getStatus().getLevel() >= 10 && _owner.hasSkill(L2Skill.SKILL_LUCKY))
				removeSkill(L2Skill.SKILL_LUCKY, false);

			removeInvalidSkills();

			_owner.sendPacket(new SkillList(_owner));
		}
	}

	/**
	 * Method used by admin commands, ConfigPlayers.AUTO_LEARN_SKILLS or class master.<br>
	 * Reward the {@link Player} with all available skills, being autoGet or general skills.
	 */
	public void rewardSkills()
	{
		rewardSkills(false);
	}

	/**
	 * Reward all currently available skills.
	 * @param storeAllSkills if true, persist even autoGet skills whose SP cost is 0. This
	 *            is needed when a player explicitly learns skills from Class Master NPCs;
	 *            otherwise those skills only exist in memory and can disappear after relog.
	 */
	public void rewardSkills(boolean storeAllSkills)
	{
		for (final GeneralSkillNode skill : getAllAvailableSkills())
		{
			if (skill.getId() == L2Skill.SKILL_DIVINE_INSPIRATION && ConfigPlayers.DIVINE_SP_BOOK_NEEDED)
				continue;

			addSkill(skill.getSkill(), storeAllSkills || skill.getCost() != 0, true);
		}

		if (_owner.getStatus().getLevel() >= 10 && _owner.hasSkill(L2Skill.SKILL_LUCKY))
			removeSkill(L2Skill.SKILL_LUCKY, false);

		removeInvalidSkills();

		_owner.sendPacket(new SkillList(_owner));
	}

	/**
	 * Delete all invalid {@link L2Skill}s for this {@link Player}.<br>
	 * <br>
	 * A skill is considered invalid when the level of obtention of the skill is superior to 9 compared to player level (expertise skill obtention level is compared to player level without any penalty).<br>
	 * <br>
	 * It is then either deleted, or level is refreshed.
	 */
	public void removeInvalidSkills()
	{
		if (getSkills().isEmpty())
			return;

		final Set<Integer> templateSkillIds = new HashSet<>();

		final Map<Integer, GeneralSkillNode> availableSkills = new HashMap<>();

		final int playerLevel = _owner.getStatus().getLevel();

		for (GeneralSkillNode skillNode : _owner.getTemplate().getSkills())
		{
			templateSkillIds.add(skillNode.getId());

			if (skillNode.getMinLvl() <= playerLevel + (skillNode.getId() == L2Skill.SKILL_EXPERTISE ? 0 : 9))
			{
				final GeneralSkillNode existingNode = availableSkills.get(skillNode.getId());
				if (existingNode == null || skillNode.getValue() > existingNode.getValue())
					availableSkills.put(skillNode.getId(), skillNode);
			}
		}

		for (final L2Skill skill : getSkills().values())
		{
			if (!templateSkillIds.contains(skill.getId()))
				continue;

			final GeneralSkillNode availableSkill = availableSkills.get(skill.getId());
			if (availableSkill == null)
			{
				removeSkill(skill.getId(), true);
				continue;
			}

			final int maxLevel = SkillTable.getInstance().getMaxLevel(skill.getId());

			if (skill.getLevel() > maxLevel)
			{
				if ((playerLevel < 76 || availableSkill.getValue() < maxLevel) && skill.getLevel() > availableSkill.getValue())
					addSkill(availableSkill.getSkill(), true);
			}
			else if (skill.getLevel() > availableSkill.getValue())
				addSkill(availableSkill.getSkill(), true);
		}
	}

	/**
	 * Regive all skills which aren't saved to database, like Noble, Hero, Clan Skills.<br>
	 * <b>Do not call this on enterworld or char load.</b>.
	 */
	public void regiveTemporarySkills()
	{
		if (_owner.isNoble())
			_owner.setNoble(true, false);

		if (_owner.isHero())
			_owner.setHero(true);

		if (_owner.getClan() != null)
		{
			_owner.getClan().checkAndAddClanSkills(_owner);

			if (_owner.getClan().getLevel() >= ConfigSiege.MINIMUM_CLAN_LEVEL && _owner.isClanLeader())
				addSiegeSkills();
		}

		_owner.getInventory().reloadEquippedItems();

		if (_owner.getDeathPenaltyBuffLevel() > 0)
			addSkill(SkillTable.getInstance().getInfo(5076, _owner.getDeathPenaltyBuffLevel()), false);
	}

	public void addSiegeSkills()
	{
		for (final L2Skill sk : SkillTable.getInstance().getSiegeSkills(_owner.isNoble()))
			addSkill(sk, false);
	}

	public void removeSiegeSkills()
	{
		for (final L2Skill sk : SkillTable.getInstance().getSiegeSkills(_owner.isNoble()))
			removeSkill(sk.getId(), false);
	}

	/**
	 * @return a {@link List} of all available autoGet {@link GeneralSkillNode}s <b>of maximal level</b> for this {@link Player}.
	 */
	public List<GeneralSkillNode> getAvailableAutoGetSkills()
	{
		final List<GeneralSkillNode> result = new ArrayList<>();

		_owner.getTemplate().getSkills().stream().filter(s -> s.getMinLvl() <= _owner.getStatus().getLevel() && s.getCost() == 0).collect(Collectors.groupingBy(s -> s.getId(), Collectors.maxBy(COMPARE_SKILLS_BY_LVL))).forEach((i, s) ->
		{
			if (_owner.getSkillLevel(i) < s.get().getValue())
				result.add(s.get());
		});
		return result;
	}

	/**
	 * @return a {@link List} of available {@link GeneralSkillNode}s (only general) for this {@link Player}.
	 */
	public List<GeneralSkillNode> getAvailableSkills()
	{
		final List<GeneralSkillNode> result = new ArrayList<>();

		_owner.getTemplate().getSkills().stream().filter(s -> s.getMinLvl() <= _owner.getStatus().getLevel() && s.getCost() != 0).forEach(s ->
		{
			if (_owner.getSkillLevel(s.getId()) == s.getValue() - 1)
				result.add(s);
		});
		return result;
	}

	/**
	 * @return a {@link List} of all available {@link GeneralSkillNode}s (being general or autoGet) <b>of maximal level</b> for this {@link Player}.
	 */
	public List<GeneralSkillNode> getAllAvailableSkills()
	{
		final List<GeneralSkillNode> result = new ArrayList<>();

		_owner.getTemplate().getSkills().stream().filter(s -> s.getMinLvl() <= _owner.getStatus().getLevel()).collect(Collectors.groupingBy(s -> s.getId(), Collectors.maxBy(COMPARE_SKILLS_BY_LVL))).forEach((i, s) ->
		{
			if (_owner.getSkillLevel(i) < s.get().getValue())
				result.add(s.get());
		});
		return result;
	}

	/**
	 * Retrieve next lowest level skill to learn, based on current player level and skill sp cost.
	 * @return the required level for next {@link GeneralSkillNode} to learn for this {@link Player}.
	 */
	public int getRequiredLevelForNextSkill()
	{
		return _owner.getTemplate().getSkills().stream().filter(s -> s.getMinLvl() > _owner.getStatus().getLevel() && s.getCost() != 0).min(COMPARE_SKILLS_BY_MIN_LVL).map(s -> s.getMinLvl()).orElse(0);
	}

	// --------------------------------------------------------------------
	// Reuse timestamps
	// --------------------------------------------------------------------

	/**
	 * Index according to skill id the current timestamp of use.
	 * @param skill the skill.
	 * @param reuse delay in milliseconds.
	 */
	public void addTimeStamp(L2Skill skill, long reuse)
	{
		_reuseTimeStamps.put(skill.getReuseHashCode(), new Timestamp(skill, reuse));
	}

	/**
	 * Index according to skill this {@link Timestamp} instance for restoration purposes only.
	 * @param skill the skill.
	 * @param reuse delay in milliseconds.
	 * @param systime the absolute timestamp (ms).
	 */
	public void addTimeStamp(L2Skill skill, long reuse, long systime)
	{
		_reuseTimeStamps.put(skill.getReuseHashCode(), new Timestamp(skill, reuse, systime));
	}

	/**
	 * Remove and return the timestamp entry for the given skill, if any.
	 */
	public Timestamp removeTimeStamp(L2Skill skill)
	{
		return _reuseTimeStamps.remove(skill.getReuseHashCode());
	}

	/**
	 * Drop every recorded reuse timestamp (used on death / logout).
	 */
	public void clearReuseTimeStamps()
	{
		_reuseTimeStamps.clear();
	}

	// --------------------------------------------------------------------
	// Short-buff (player target buff indicator)
	// --------------------------------------------------------------------

	public void shortBuffStatusUpdate(int magicId, int level, int time)
	{
		if (_shortBuffTask != null)
		{
			_shortBuffTask.cancel(false);
			_shortBuffTask = null;
		}

		_shortBuffTask = ThreadPool.schedule(() ->
		{
			_owner.sendPacket(new ShortBuffStatusUpdate(0, 0, 0));
			setShortBuffTaskSkillId(0);
		}, time * 1000L);
		setShortBuffTaskSkillId(magicId);

		_owner.sendPacket(new ShortBuffStatusUpdate(magicId, level, time));
	}
}
