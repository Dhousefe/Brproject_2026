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
import ext.mods.extensions.api.IFarmZoneInfo;
import ext.mods.extensions.hooks.BattleBossHooks;
import ext.mods.extensions.hooks.FarmEventHooks;
import ext.mods.extensions.hooks.PlayerGodHooks;
import ext.mods.extensions.hooks.TournamentHooks;
import ext.mods.extensions.listener.manager.CreatureListenerManager;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.gameserver.custom.data.PvPData;
import ext.mods.gameserver.custom.data.PvPData.ColorSystem;
import ext.mods.gameserver.custom.data.PvPData.RewardSystem;
import ext.mods.gameserver.data.manager.AntiFeedManager;
import ext.mods.gameserver.data.manager.CursedWeaponManager;
import ext.mods.gameserver.data.manager.PcCafeManager;
import ext.mods.gameserver.data.manager.ZoneManager;
import ext.mods.gameserver.data.SkillTable;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.actors.MissionType;
import ext.mods.gameserver.enums.skills.EffectType;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Playable;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.Summon;
import ext.mods.gameserver.model.entity.events.capturetheflag.CTFEvent;
import ext.mods.gameserver.model.entity.events.deathmatch.DMEvent;
import ext.mods.gameserver.model.entity.events.lastman.LMEvent;
import ext.mods.gameserver.model.entity.events.teamvsteam.TvTEvent;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.item.kind.Item;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.model.zone.type.RandomZone;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.ExAutoSoulShot;
import ext.mods.gameserver.network.serverpackets.EtcStatusUpdate;
import ext.mods.gameserver.network.serverpackets.RelationChanged;
import ext.mods.gameserver.network.serverpackets.SystemMessage;
import ext.mods.gameserver.network.serverpackets.UserInfo;
import ext.mods.gameserver.skills.Formulas;
import ext.mods.gameserver.skills.L2Skill;
import ext.mods.gameserver.taskmanager.PvpFlagTaskManager;
import ext.mods.gameserver.taskmanager.WaterTaskManager;

/**
 * Combat / Damage / Death / PvP / PK / karma state and actions for a {@link Player}.
 *
 * <p>Owns the bodies of {@link #doDie(Creature)}, {@link #doRevive()},
 * {@link #doRevive(double)}, {@link #reduceCurrentHp(double, Creature, boolean, boolean, L2Skill)},
 * {@link #onKillUpdatePvPKarma(Playable)}, {@link #updatePvPStatus()},
 * {@link #onDieDropItem(Creature)}, {@link #updateKarmaLoss(long)},
 * {@link #calculateDeathPenaltyBuffLevel(Creature)},
 * {@link #reduceDeathPenaltyBuffLevel()}, {@link #removeDeathPenaltyBuffLevel()},
 * {@link #checkItemRestriction()}, {@link #isInEnchanterZone()},
 * and {@link #getRelation(Player)}.</p>
 */
public final class PlayerCombat
{
	private final Player _owner;

	public PlayerCombat(Player owner)
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

	/**
	 * Compute the relation bitmask between this {@link Player} and another.
	 * Used by the client to render PvP / PK / siege / war / clan-leader visuals.
	 */
	public int getRelation(Player target)
	{
		int result = 0;

		if (_owner.getPvpFlag() != 0)
			result |= RelationChanged.RELATION_PVP_FLAG;
		if (_owner.getKarma() > 0)
			result |= RelationChanged.RELATION_HAS_KARMA;

		if (_owner.isClanLeader())
			result |= RelationChanged.RELATION_LEADER;

		if (_owner.getSiegeState() != 0)
		{
			result |= RelationChanged.RELATION_INSIEGE;
			if (_owner.getSiegeState() != target.getSiegeState())
				result |= RelationChanged.RELATION_ENEMY;
			else
				result |= RelationChanged.RELATION_ALLY;
			if (_owner.getSiegeState() == 1)
				result |= RelationChanged.RELATION_ATTACKER;
		}

		if (_owner.getClan() != null && target.getClan() != null)
		{
			if (target.getPledgeType() != Clan.SUBUNIT_ACADEMY && _owner.getPledgeType() != Clan.SUBUNIT_ACADEMY && target.getClan().isAtWarWith(_owner.getClan().getClanId()))
			{
				result |= RelationChanged.RELATION_1SIDED_WAR;
				if (_owner.getClan().isAtWarWith(target.getClan().getClanId()))
					result |= RelationChanged.RELATION_MUTUAL_WAR;
			}
		}
		return result;
	}

	public void reduceCurrentHp(double value, Creature attacker, boolean awake, boolean isDOT, L2Skill skill)
	{
		if (skill != null)
			_owner.getStatus().reduceHp(value, attacker, awake, isDOT, skill.isToggle(), skill.getDmgDirectlyToHP());
		else
			_owner.getStatus().reduceHp(value, attacker, awake, isDOT, false, false);
		CreatureListenerManager.getInstance().notifyHpDamage(_owner, value, attacker, skill);
	}

	public boolean doDie(Creature killer)
	{
		if (BattleBossState.isInEvent(_owner))
		{
			_owner.startFakeDeath();
			_owner.abortAll(true);
			_owner.setIsImmobilized(true);
			_owner.setInvul(true);
			_owner.getStatus().stopHpMpRegeneration();
			_owner.getStatus().broadcastStatusUpdate();

			BattleBossHooks.get().onPlayerDeath(_owner);

			return true;
		}

		if (!_owner.callSuperDoDie(killer))
			return false;

		if (TournamentState.isIn(_owner) && TournamentState.opponents(_owner) != null && !TournamentState.opponents(_owner).isEmpty())
		{
			_owner.startFakeDeath();
			_owner.abortAll(true);
			_owner.setIsImmobilized(true);
			_owner.setInvul(true);
			reduceCurrentHp(_owner.getStatus().getMaxHp() + 1, _owner, false, false, null);
			_owner.getStatus().stopHpMpRegeneration();
			_owner.getStatus().broadcastStatusUpdate();
			TournamentHooks.get().onPlayerDeath(_owner);
			return true;
		}

		if (_owner.isMounted())
			_owner.stopFeed();

		_owner.clearCharges();

		synchronized (_owner)
		{
			if (_owner.isFakeDeath())
				_owner.stopFakeDeath(true);
		}

		if (killer != null)
		{
			final Player pk = killer.getActingPlayer();

			if (pk != null)
			{
				CTFEvent.getInstance().onKill(killer, _owner);
				DMEvent.getInstance().onKill(killer, _owner);
				LMEvent.getInstance().onKill(killer, _owner);
				TvTEvent.getInstance().onKill(killer, _owner);
				PlayerGodHooks.get().onKill(pk);
				PlayerListenerManager.getInstance().notifyPvpPkKill(pk, _owner, true);
				CreatureListenerManager.getInstance().notifyKill(pk, _owner);
				CreatureListenerManager.getInstance().notifyDeath(pk, _owner);
			}

			_owner.setExpBeforeDeath(0);

			if (_owner.isCursedWeaponEquipped())
				CursedWeaponManager.getInstance().drop(_owner.getCursedWeaponEquippedId(), killer);
			else
			{
				if (pk == null || !pk.isCursedWeaponEquipped())
				{
					onDieDropItem(killer);

					if (!_owner.isInArena())
					{
						if (pk != null && pk.getClan() != null && _owner.getClan() != null && !_owner.isAcademyMember() && !pk.isAcademyMember())
						{
							if (_owner.getClan().isAtWarWith(pk.getClanId()) && pk.getClan().isAtWarWith(_owner.getClan().getClanId()))
							{
								if (AntiFeedManager.getInstance().check(killer, _owner))
								{
									if (_owner.getClan().getReputationScore() > 0)
										pk.getClan().addReputationScore(1);
									if (pk.getClan().getReputationScore() > 0)
										_owner.getClan().takeReputationScore(1);
								}
							}
						}
					}

					if (ConfigPlayers.ALLOW_DELEVEL && (!_owner.hasSkill(L2Skill.SKILL_LUCKY) || _owner.getStatus().getLevel() > 9))
						_owner.applyDeathPenalty(pk != null && _owner.getClan() != null && pk.getClan() != null && (_owner.getClan().isAtWarWith(pk.getClanId()) || pk.getClan().isAtWarWith(_owner.getClanId())), pk != null);
				}
			}
		}

		_owner.getCubicList().stopCubics(false);

		if (_owner.getFusionSkill() != null)
			_owner.getCast().stop();

		_owner.forEachKnownType(Creature.class, creature -> creature.getFusionSkill() != null && creature.getFusionSkill().getTarget() == _owner, creature -> creature.getCast().stop());

		calculateDeathPenaltyBuffLevel(killer);

		WaterTaskManager.getInstance().remove(_owner);

		if (_owner.isPhoenixBlessed())
			_owner.reviveRequest(_owner, null, false);

		int[] values =
		{
			6645,
			6646,
			6647
		};

		for (int itemId : values)
		{
			_owner.removeAutoSoulShot(itemId);
			_owner.sendPacket(new ExAutoSoulShot(itemId, 0));
		}

		_owner.updateEffectIcons();
		_owner.disableBeastShots();
		AntiFeedManager.getInstance().setLastDeathTime(_owner.getObjectId());
		_owner.getMissions().update(MissionType.DEATHS);

		return true;
	}

	private void onDieDropItem(Creature killer)
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

	public void updateKarmaLoss(long exp)
	{
		if (!_owner.isCursedWeaponEquipped() && _owner.getKarma() > 0)
		{
			final int karmaLost = Formulas.calculateKarmaLost(_owner.getStatus().getLevel(), exp);
			if (karmaLost > 0)
				_owner.setKarma(_owner.getKarma() - karmaLost);
		}
	}

	/**
	 * This method is used to update PvP counter, or PK counter / add Karma if necessary.<br>
	 * It also updates clan kills/deaths counters on siege.
	 * @param target The L2Playable victim.
	 */
	public void onKillUpdatePvPKarma(Playable target)
	{
		if (target == null)
			return;

		final Player targetPlayer = target.getActingPlayer();
		if (targetPlayer == null || targetPlayer == _owner)
			return;

		if (CTFEvent.getInstance().isStarted() && CTFEvent.getInstance().isPlayerParticipant(_owner.getObjectId()) || DMEvent.getInstance().isStarted() && DMEvent.getInstance().isPlayerParticipant(_owner.getObjectId()) || LMEvent.getInstance().isStarted() && LMEvent.getInstance().isPlayerParticipant(_owner.getObjectId()) || TvTEvent.getInstance().isStarted() && TvTEvent.getInstance().isPlayerParticipant(_owner.getObjectId()))
			return;
		if (TournamentState.isIn(_owner))
			return;
		if (BattleBossState.isInEvent(_owner))
			return;
		if (_owner.isCursedWeaponEquipped() && target instanceof Player)
		{
			CursedWeaponManager.getInstance().increaseKills(_owner.getCursedWeaponEquippedId());
			return;
		}

		if (_owner.isInDuel() && targetPlayer.isInDuel())
			return;

		if (_owner.isInsideZone(ZoneId.PVP) && targetPlayer.isInsideZone(ZoneId.PVP))
		{
			if (target instanceof Player && _owner.getSiegeState() > 0 && targetPlayer.getSiegeState() > 0 && _owner.getSiegeState() != targetPlayer.getSiegeState())
			{
				final Clan killerClan = _owner.getClan();
				if (killerClan != null)
					killerClan.setSiegeKills(killerClan.getSiegeKills() + 1);

				final Clan targetClan = targetPlayer.getClan();
				if (targetClan != null)
					targetClan.setSiegeDeaths(targetClan.getSiegeDeaths() + 1);
			}
			return;
		}

		if (_owner.checkIfPvP(target) || (targetPlayer.getClan() != null && _owner.getClan() != null && _owner.getClan().isAtWarWith(targetPlayer.getClanId()) && targetPlayer.getClan().isAtWarWith(_owner.getClanId()) && targetPlayer.getPledgeType() != Clan.SUBUNIT_ACADEMY && _owner.getPledgeType() != Clan.SUBUNIT_ACADEMY) || (targetPlayer.getKarma() > 0 && ConfigPlayers.KARMA_AWARD_PK_KILL))
		{
			if (target instanceof Player && AntiFeedManager.getInstance().check(_owner, target))
			{
				FarmEventHooks.get().onPvPKill(_owner, (Player) target);
				_owner.setPvpKills(_owner.getPvpKills() + 1);

				for (RewardSystem kills : PvPData.getInstance().getReward())
				{
					for (IntIntHolder reward : kills.reward())
					{
						if (reward.getId() > 0)
							_owner.addItem(reward.getId(), reward.getValue(), true);
					}
				}

				for (ColorSystem pvpColor : PvPData.getInstance().getColor())
				{
					if (_owner.getPvpKills() >= pvpColor.pvpAmount())
					{
						_owner.getAppearance().setNameColor(pvpColor.nameColor());
						_owner.getAppearance().setTitleColor(pvpColor.titleColor());
						_owner.broadcastUserInfo();
					}
				}

				PcCafeManager.getInstance().onPlayerPvPKill(_owner);
				_owner.getMissions().update(MissionType.PVP);
				_owner.sendPacket(new UserInfo(_owner));
			}
		}
		else if (targetPlayer.getKarma() == 0 && targetPlayer.getPvpFlag() == 0)
		{
			if (target instanceof Player || AntiFeedManager.getInstance().check(_owner, target))
				_owner.setPkKills(_owner.getPkKills() + 1);

			_owner.setKarma(_owner.getKarma() + Formulas.calculateKarmaGain(_owner.getPkKills(), target instanceof Summon));
			checkItemRestriction();
			_owner.getMissions().update(MissionType.PK);
			PvpFlagTaskManager.getInstance().remove(_owner, true);
		}
	}

	public void updatePvPStatus()
	{
		if (TournamentState.isIn(_owner))
			return;

		if (_owner.isInsideZone(ZoneId.PVP))
			return;

		if (isInEnchanterZone())
		{
			if (_owner.getPvpFlag() == 0)
				_owner.updatePvPFlag(1);
			return;
		}

		PvpFlagTaskManager.getInstance().add(_owner, ConfigPlayers.PVP_NORMAL_TIME);

		if (_owner.getPvpFlag() == 0)
			_owner.updatePvPFlag(1);
	}

	public void updatePvPStatus(Creature target)
	{
		final Player player = target.getActingPlayer();
		if (player == null)
			return;

		if (TournamentState.isIn(_owner))
			return;

		if (_owner.isInDuel() && player.getDuelId() == _owner.getDuelId())
			return;

		if ((!_owner.isInsideZone(ZoneId.PVP) || !target.isInsideZone(ZoneId.PVP)) && player.getKarma() == 0)
		{
			if (isInEnchanterZone())
			{
				if (_owner.getPvpFlag() == 0)
					_owner.updatePvPFlag(1);
				return;
			}

			PvpFlagTaskManager.getInstance().add(_owner, _owner.checkIfPvP(player) ? ConfigPlayers.PVP_PVP_TIME : ConfigPlayers.PVP_NORMAL_TIME);

			if (_owner.getPvpFlag() == 0)
				_owner.updatePvPFlag(1);
		}
	}

	/**
	 * Verifica se o player está dentro de uma EnchanterZone ativa.
	 * @return true se está em EnchanterZone ativa, false caso contrário
	 */
	public boolean isInEnchanterZone()
	{
		if (!_owner.isInsideZone(ZoneId.RANDOM))
			return false;

		try
		{
			RandomZone zone = ZoneManager.getInstance().getZone(_owner, RandomZone.class);
			if (zone == null || !zone.isActive())
				return false;

			IFarmZoneInfo zoneData = FarmEventHooks.get().getZoneDataForZone(zone);
			return zoneData != null && zoneData.isEnchanterZone();
		}
		catch (Exception e)
		{
			return false;
		}
	}

	public void doRevive()
	{
		_owner.callSuperDoRevive();
		_owner.stopEffects(EffectType.CHARM_OF_COURAGE);
		_owner.sendPacket(new EtcStatusUpdate(_owner));
		_owner.setReviveRequested(0);
		_owner.setRevivePower(0);

		if (_owner.isMounted())
			_owner.startFeed(_owner.getMountNpcId());

		CreatureListenerManager.getInstance().notifyRevive(_owner);
	}

	public void doRevive(double revivePower)
	{
		_owner.restoreExp(revivePower);
		doRevive();
	}

	/**
	 * Check and calculate if a new Death Penalty buff level needs to be added. If Death Penalty already applies, raise its level by 1.
	 * @param killer : The {@link Creature} who killed this {@link Player}.
	 */
	public void calculateDeathPenaltyBuffLevel(Creature killer)
	{
		if (_owner.getDeathPenaltyBuffLevel() >= 15)
			return;

		if ((_owner.getKarma() > 0 || Rnd.get(1, 100) <= ConfigPlayers.DEATH_PENALTY_CHANCE) && !(killer instanceof Player) && !(_owner.isGM()) && !(_owner.getCharmOfLuck() && (killer == null || killer.isRaidRelated())) && !_owner.isPhoenixBlessed() && !(_owner.isInsideZone(ZoneId.PVP) || _owner.isInsideZone(ZoneId.SIEGE)))
		{
			if (_owner.getDeathPenaltyBuffLevel() != 0)
				_owner.removeSkill(5076, false);

			_owner.setDeathPenaltyBuffLevel(_owner.getDeathPenaltyBuffLevel() + 1);
			_owner.addSkill(SkillTable.getInstance().getInfo(5076, _owner.getDeathPenaltyBuffLevel()), false);
			_owner.sendPacket(new EtcStatusUpdate(_owner));
			_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DEATH_PENALTY_LEVEL_S1_ADDED).addNumber(_owner.getDeathPenaltyBuffLevel()));
		}
	}

	/**
	 * Reduce the Death Penalty buff effect from this {@link Player} of 1. If it reaches 0, remove it entirely.
	 */
	public void reduceDeathPenaltyBuffLevel()
	{
		if (_owner.getDeathPenaltyBuffLevel() <= 0)
			return;

		_owner.removeSkill(5076, false);
		_owner.setDeathPenaltyBuffLevel(_owner.getDeathPenaltyBuffLevel() - 1);

		if (_owner.getDeathPenaltyBuffLevel() > 0)
		{
			_owner.addSkill(SkillTable.getInstance().getInfo(5076, _owner.getDeathPenaltyBuffLevel()), false);
			_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DEATH_PENALTY_LEVEL_S1_ADDED).addNumber(_owner.getDeathPenaltyBuffLevel()));
		}
		else
			_owner.sendPacket(SystemMessageId.DEATH_PENALTY_LIFTED);

		_owner.sendPacket(new EtcStatusUpdate(_owner));
	}

	/**
	 * Remove the Death Penalty buff effect from this {@link Player}.
	 */
	public void removeDeathPenaltyBuffLevel()
	{
		if (_owner.getDeathPenaltyBuffLevel() <= 0)
			return;

		_owner.removeSkill(5076, false);
		_owner.setDeathPenaltyBuffLevel(0);
		_owner.sendPacket(SystemMessageId.DEATH_PENALTY_LIFTED);
		_owner.sendPacket(new EtcStatusUpdate(_owner));
	}

	public void checkItemRestriction()
	{
		for (final ItemInstance item : _owner.getInventory().getPaperdollItems())
		{
			if (item.getItem().checkCondition(_owner, _owner, false))
				continue;

			_owner.useEquippableItem(item, item.isWeapon());
		}
	}
}