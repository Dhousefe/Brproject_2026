/*
* Copyleft © 2024-2026 L2Brproject
* This file is part of L2Brproject derived from aCis409/RusaCis3.8
* L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
*/
package ext.mods.gameserver.model.actor.player;

import ext.mods.commons.random.Rnd;
import ext.mods.commons.util.ArraysUtil;
import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigRates;
import ext.mods.gameserver.data.xml.PlayerLevelData;
import ext.mods.gameserver.enums.AiEventType;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.actors.MissionType;
import ext.mods.gameserver.enums.skills.EffectFlag;
import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.Summon;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.ChangeWaitType;
import ext.mods.gameserver.network.serverpackets.ConfirmDlg;
import ext.mods.gameserver.network.serverpackets.Revive;
import ext.mods.gameserver.skills.Formulas;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Death-helper surface for a {@link Player}: handles item drop on death, exp
 * penalty application/restore, fake death lifecycle, revive request flow, and
 * the bookkeeping fields used by {@link PlayerCombat#doDie(Creature)} and
 * {@link PlayerCombat#doRevive()}.
 *
 * <p>The main {@code doDie} / {@code doRevive} entry points remain on
 * {@link PlayerCombat}; this class owns the remaining death-related helpers
 * such as {@link #applyDeathPenalty(boolean, boolean)},
 * {@link #restoreExp(double)}, {@link #onDieDropItem(Creature)},
 * {@link #reviveRequest(Player, L2Skill, boolean)}, the fake-death toggles,
 * and the revive request state.</p>
 */
public final class PlayerDeathLifecycle
{
	private final Player _owner;

	public PlayerDeathLifecycle(Player owner)
	{
		_owner = owner;
	}

	/**
	 * @return The {@link Player} owning this component.
	 */
	public Player getOwner()
	{
		return _owner;
	}

	// ---------------------------------------------------------------------
	// Item drop on death
	// ---------------------------------------------------------------------

	/**
	 * Process the items that this {@link Player} drops on death, based on the
	 * killer type, karma, war state and configuration rates.
	 * @param killer : The {@link Creature} who killed this player.
	 */
	public void onDieDropItem(Creature killer)
	{
		if (killer == null)
			return;

		final Player pk = killer.getActingPlayer();
		if (_owner.getKarma() <= 0 && pk != null && pk.getClan() != null && _owner.getClan() != null && pk.getClan().isAtWarWith(_owner.getClanId()))
			return;

		if ((!_owner.isInsideZone(ZoneId.PVP) || pk == null) && (!_owner.isGM() || ConfigPlayers.KARMA_DROP_GM))
		{
			final boolean isKillerNpc = (killer instanceof Npc);
			final int pkLimit = ConfigPlayers.KARMA_PK_LIMIT;

			int dropEquip = 0;
			int dropEquipWeapon = 0;
			int dropItem = 0;
			int dropLimit = 0;
			int dropPercent = 0;

			if (_owner.getKarma() > 0 && _owner.getPkKills() >= pkLimit)
			{
				dropPercent = ConfigRates.KARMA_RATE_DROP;
				dropEquip = ConfigRates.KARMA_RATE_DROP_EQUIP;
				dropEquipWeapon = ConfigRates.KARMA_RATE_DROP_EQUIP_WEAPON;
				dropItem = ConfigRates.KARMA_RATE_DROP_ITEM;
				dropLimit = ConfigRates.KARMA_DROP_LIMIT;
			}
			else if (isKillerNpc && _owner.getStatus().getLevel() > 4 && !_owner.isFestivalParticipant())
			{
				dropPercent = ConfigRates.PLAYER_RATE_DROP;
				dropEquip = ConfigRates.PLAYER_RATE_DROP_EQUIP;
				dropEquipWeapon = ConfigRates.PLAYER_RATE_DROP_EQUIP_WEAPON;
				dropItem = ConfigRates.PLAYER_RATE_DROP_ITEM;
				dropLimit = ConfigRates.PLAYER_DROP_LIMIT;
			}

			if (dropPercent > 0 && Rnd.get(100) < dropPercent)
			{
				int dropCount = 0;
				int itemDropPercent = 0;

				for (final ItemInstance itemDrop : _owner.getInventory().getItems())
				{
					if (!itemDrop.isDropable() || itemDrop.isShadowItem() || itemDrop.getItemId() == 57 || itemDrop.getItem().getType2() == Item.TYPE2_QUEST || (_owner.getSummon() != null && _owner.getSummon().getControlItemId() == itemDrop.getItemId()) || ArraysUtil.contains(ConfigPlayers.KARMA_NONDROPPABLE_ITEMS, itemDrop.getItemId()) || ArraysUtil.contains(ConfigPlayers.KARMA_NONDROPPABLE_PET_ITEMS, itemDrop.getItemId()))
						continue;

					if (itemDrop.isEquipped())
					{
						itemDropPercent = itemDrop.getItem().getType2() == Item.TYPE2_WEAPON ? dropEquipWeapon : dropEquip;
						_owner.getInventory().unequipItemInSlot(itemDrop.getLocationSlot());
					}
					else
						itemDropPercent = dropItem;

					if (Rnd.get(100) < itemDropPercent)
					{
						_owner.dropItem(itemDrop, true);

						if (++dropCount >= dropLimit)
							break;
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------------
	// Exp penalty / restore
	// ---------------------------------------------------------------------

	/**
	 * Calculate the xp loss and the karma decrease.
	 * @param atWar : If true, use clan war penalty system instead of regular system.
	 * @param killedByPlayable : If true, we use specific rules if killed by playable only.
	 */
	public void applyDeathPenalty(boolean atWar, boolean killedByPlayable)
	{
		if (TournamentState.isIn(_owner))
			return;

		if (_owner.isInsideZone(ZoneId.PVP))
		{
			if (_owner.isInsideZone(ZoneId.SIEGE))
			{
				if (_owner.isAffected(EffectFlag.CHARM_OF_COURAGE))
				{
					_owner.stopEffects(EffectType.CHARM_OF_COURAGE);
					return;
				}
			}
			else if (killedByPlayable)
				return;
		}

		final int lvl = _owner.getStatus().getLevel();

		double percentLost = PlayerLevelData.getInstance().getPlayerLevel(lvl).expLossAtDeath();

		if (_owner.getKarma() > 0)
			percentLost *= ConfigRates.RATE_KARMA_EXP_LOST;

		if (_owner.isFestivalParticipant() || atWar || _owner.isInsideZone(ZoneId.SIEGE))
			percentLost /= 4.0;

		long lostExp = 0;

		final int maxLevel = PlayerLevelData.getInstance().getMaxLevel();
		if (lvl < maxLevel)
			lostExp = Math.round((_owner.getStatus().getExpForLevel(lvl + 1) - _owner.getStatus().getExpForLevel(lvl)) * percentLost / 100);
		else
			lostExp = Math.round((_owner.getStatus().getExpForLevel(maxLevel) - _owner.getStatus().getExpForLevel(maxLevel - 1)) * percentLost / 100);

		_owner.setExpBeforeDeath(_owner.getStatus().getExp());

		_owner.updateKarmaLoss(lostExp);

		_owner.getStatus().addExp(-lostExp);
	}

	/**
	 * Restore the experience this Player has lost and sends StatusUpdate packet.
	 * @param restorePercent The specified % of restored experience.
	 */
	public void restoreExp(double restorePercent)
	{
		if (_owner.getExpBeforeDeath() > 0)
		{
			_owner.getStatus().addExp((int) Math.round((_owner.getExpBeforeDeath() - _owner.getStatus().getExp()) * restorePercent / 100));
			_owner.setExpBeforeDeath(0);
		}
	}

	// ---------------------------------------------------------------------
	// Fake death
	// ---------------------------------------------------------------------

	public final boolean isFakeDeath()
	{
		return _owner.isFakeDeath();
	}

	public final void setIsFakeDeath(boolean value)
	{
		_owner.setIsFakeDeath(value);
	}

	public final void startFakeDeath()
	{
		setIsFakeDeath(true);
		_owner.getAI().notifyEvent(AiEventType.SAT_DOWN, null, null);
		_owner.broadcastPacket(new ChangeWaitType(_owner, ChangeWaitType.WT_START_FAKEDEATH));
	}

	public final void stopFakeDeath(boolean removeEffects)
	{
		if (removeEffects)
			_owner.stopEffects(EffectType.FAKE_DEATH);

		setIsFakeDeath(false);
		setRecentFakeDeath();

		_owner.getAI().notifyEvent(AiEventType.STOOD_UP, null, null);
		_owner.broadcastPacket(new ChangeWaitType(_owner, ChangeWaitType.WT_STOP_FAKEDEATH));
		_owner.broadcastPacket(new Revive(_owner));
	}

	/**
	 * Set protection from agro mobs when getting up from fake death, according settings.
	 */
	public void setRecentFakeDeath()
	{
		_owner.setRecentFakeDeath();
	}

	public void clearRecentFakeDeath()
	{
		_owner.clearRecentFakeDeath();
	}

	public boolean isRecentFakeDeath()
	{
		return _owner.isRecentFakeDeath();
	}

	public final boolean isAlikeDead()
	{
		return _owner.isDead() || isFakeDeath();
	}

	// ---------------------------------------------------------------------
	// Revive request flow
	// ---------------------------------------------------------------------

	public void reviveRequest(Player reviver, L2Skill skill, boolean isPet)
	{
		if (_owner.getReviveRequested() == 1)
		{
			if (_owner.isRevivePet() == isPet)
				reviver.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED);
			else
			{
				if (isPet)
					reviver.sendPacket(SystemMessageId.CANNOT_RES_PET2);
				else
					reviver.sendPacket(SystemMessageId.MASTER_CANNOT_RES);
			}
			return;
		}

		if (isPet)
		{
			final Summon summon = _owner.getSummon();
			if (summon != null && summon.isDead())
			{
				_owner.setReviveRequested(1);
				_owner.setRevivePower((summon.isPhoenixBlessed()) ? 100. : Formulas.calcRevivePower(reviver, skill.getPower()));
				_owner.setRevivePet(isPet);

				_owner.sendPacket(new ConfirmDlg(SystemMessageId.RESSURECTION_REQUEST_BY_S1).addCharName(reviver));
			}
		}
		else
		{
			if (_owner.isDead())
			{
				_owner.setReviveRequested(1);
				_owner.setRevivePower((_owner.isPhoenixBlessed()) ? 100. : Formulas.calcRevivePower(reviver, skill.getPower()));
				_owner.setRevivePet(isPet);

				_owner.sendPacket(new ConfirmDlg(SystemMessageId.RESSURECTION_REQUEST_BY_S1).addCharName(reviver));
			}
		}
	}

	public void reviveAnswer(int answer)
	{
		if (_owner.getReviveRequested() != 1 || (!_owner.isDead() && !_owner.isRevivePet()) || (_owner.isRevivePet() && _owner.getSummon() != null && !_owner.getSummon().isDead()))
			return;

		if (answer == 0 && _owner.isPhoenixBlessed())
			_owner.stopPhoenixBlessing(null);
		else if (answer == 1)
		{
			if (!_owner.isRevivePet())
			{
				if (_owner.getRevivePower() != 0)
					_owner.doRevive(_owner.getRevivePower());
				else
					_owner.doRevive();

				_owner.getMissions().update(MissionType.RESSURECTED);
			}
			else if (_owner.getSummon() != null)
			{
				if (_owner.getRevivePower() != 0)
					_owner.getSummon().doRevive(_owner.getRevivePower());
				else
					_owner.getSummon().doRevive();
			}
		}
		_owner.setReviveRequested(0);
		_owner.setRevivePower(0);
	}

	public boolean isReviveRequested()
	{
		return _owner.getReviveRequested() == 1;
	}

	public boolean isRevivingPet()
	{
		return _owner.isRevivePet();
	}

	public void removeReviving()
	{
		_owner.setReviveRequested(0);
		_owner.setRevivePower(0);
	}
}