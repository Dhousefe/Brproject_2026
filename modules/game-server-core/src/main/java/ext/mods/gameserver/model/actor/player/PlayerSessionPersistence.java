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
 */
package ext.mods.gameserver.model.actor.player;

import java.util.Map;

import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigProject;
import ext.mods.extensions.hooks.SafeDisconnectHooks;
import ext.mods.extensions.listener.manager.PlayerListenerManager;
import ext.mods.gameserver.communitybbs.model.Forum;
import ext.mods.gameserver.data.SkillTable.FrequentSkill;
import ext.mods.gameserver.data.manager.CursedWeaponManager;
import ext.mods.gameserver.data.manager.RelationManager;
import ext.mods.gameserver.data.manager.SevenSignsManager;
import ext.mods.gameserver.data.xml.AdminData;
import ext.mods.gameserver.data.xml.ScriptData;
import ext.mods.gameserver.enums.CabalType;
import ext.mods.gameserver.enums.MessageType;
import ext.mods.gameserver.enums.RestartType;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.WorldObject;
import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.StaticObject;
import ext.mods.gameserver.model.entity.autofarm.AutoFarmManager;
import ext.mods.gameserver.model.entity.events.capturetheflag.CTFEvent;
import ext.mods.gameserver.model.entity.events.deathmatch.DMEvent;
import ext.mods.gameserver.model.entity.events.lastman.LMEvent;
import ext.mods.gameserver.model.entity.events.teamvsteam.TvTEvent;
import ext.mods.gameserver.model.memo.PlayerMemo;
import ext.mods.gameserver.model.olympiad.OlympiadManager;
import ext.mods.gameserver.model.pledge.ClanMember;
import ext.mods.gameserver.network.GameClient;
import ext.mods.gameserver.network.serverpackets.LeaveWorld;
import ext.mods.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import ext.mods.gameserver.network.serverpackets.ServerClose;
import ext.mods.gameserver.scripting.Quest;
import ext.mods.gameserver.skills.AbstractEffect;
import ext.mods.gameserver.taskmanager.AttackStanceTaskManager;
import ext.mods.gameserver.taskmanager.PvpFlagTaskManager;
import ext.mods.gameserver.taskmanager.ShadowItemTaskManager;
import ext.mods.gameserver.taskmanager.WaterTaskManager;

/**
 * Lifecycle persistence surface for a {@link Player}: login / logout, online status,
 * account / memo accessors, character saving and on-enter hook.
 * <p>
 * Extracted from {@code Player} (att-ver-3.0 onda 6 — session/persistence decomposition).
 */
public final class PlayerSessionPersistence
{
	private static final CLogger LOGGER = new CLogger(PlayerSessionPersistence.class.getName());

	private final Player _owner;

	public PlayerSessionPersistence(Player owner)
	{
		_owner = owner;
	}

	// ========== Online Status ==========

	/**
	 * Set the online Flag to True or False and update the characters table of the database
	 * with online status and lastAccess (called when login and logout).
	 * @param isOnline
	 * @param updateInDb
	 */
	public void setOnlineStatus(boolean isOnline, boolean updateInDb)
	{
		_owner.setOnlineStatus(isOnline, updateInDb);
	}

	/**
	 * Update the characters table of the database with online status and lastAccess
	 * of this Player (called when login and logout).
	 */
	public void updateOnlineStatus()
	{
		_owner.updateOnlineStatus();
	}

	/**
	 * @return True if the Player is online.
	 */
	public boolean isOnline()
	{
		return _owner.isOnline();
	}

	/**
	 * @return an int interpretation of online status.
	 */
	public int isOnlineInt()
	{
		return _owner.isOnlineInt();
	}

	// ========== Online Time ==========

	public long getOnlineTime()
	{
		return _owner.getOnlineTime();
	}

	public void setOnlineTime(long time)
	{
		_owner.setOnlineTime(time);
	}

	public long getOnlineBeginTime()
	{
		return _owner.getOnlineBeginTime();
	}

	// ========== Last Access ==========

	public void setLastAccess(long lastAccess)
	{
		_owner.setLastAccess(lastAccess);
	}

	public long getLastAccess()
	{
		return _owner.getLastAccess();
	}

	// ========== Account ==========

	public String getAccountName()
	{
		return _owner.getAccountName();
	}

	public String getAccountNamePlayer()
	{
		return _owner.getAccountNamePlayer();
	}

	public Map<Integer, String> getAccountChars()
	{
		return _owner.getAccountChars();
	}

	// ========== Memo ==========

	public PlayerMemo getMemos()
	{
		return _owner.getMemos();
	}

	public Forum getMemo()
	{
		return _owner.getMemo();
	}

	// ========== Persistence (store) ==========

	/**
	 * Update Player stats in the characters table of the database.
	 * @param storeActiveEffects
	 */
	public synchronized void store(boolean storeActiveEffects)
	{
		_owner.store(storeActiveEffects);
	}

	public synchronized void store()
	{
		_owner.store();
	}

	public void storeCharBase()
	{
		_owner.storeCharBase();
	}

	public void storeEffect(boolean storeEffects)
	{
		_owner.storeEffect(storeEffects);
	}

	// ========== Logout ==========

	/**
	 * Close the active connection with the {@link GameClient} linked to this {@link Player}.
	 * @param closeClient : If true, the client is entirely closed. Otherwise, the client is sent back to login.
	 */
	public void logout(boolean closeClient)
	{
		SafeDisconnectHooks.get().markExpectedLogout(_owner);
		final GameClient client = _owner.getClient();
		if (client == null)
			return;

		if (client.isDetached())
			client.cleanMe(true);
		else if (!client.getConnection().isClosed())
			client.close((closeClient) ? LeaveWorld.STATIC_PACKET : ServerClose.STATIC_PACKET);
	}

	// ========== On Player Enter ==========

	public void onPlayerEnter()
	{
		if (_owner.isCursedWeaponEquipped())
			CursedWeaponManager.getInstance().getCursedWeapon(_owner.getCursedWeaponEquippedId()).cursedOnLogin();

		if (!_owner.isGM() && !ConfigProject.CATACOMBS_IN_ANY_PERIOD && _owner.isIn7sDungeon())
		{
			if (SevenSignsManager.getInstance().isSealValidationPeriod() || SevenSignsManager.getInstance().isCompResultsPeriod())
			{
				if (SevenSignsManager.getInstance().getPlayerCabal(_owner.getObjectId()) != SevenSignsManager.getInstance().getWinningCabal())
				{
					_owner.teleportTo(RestartType.TOWN);
					_owner.setIsIn7sDungeon(false);
				}
			}
			else if (SevenSignsManager.getInstance().getPlayerCabal(_owner.getObjectId()) == CabalType.NORMAL)
			{
				_owner.teleportTo(RestartType.TOWN);
				_owner.setIsIn7sDungeon(false);
			}
		}

		_owner.getPunishment().handle();

		if (_owner.isGM())
		{
			if (_owner.isInvul())
				_owner.sendMessage(_owner.getSysString(10_014));

			if (!_owner.getAppearance().isVisible())
				_owner.sendMessage(_owner.getSysString(10_015));

			if (_owner.isBlockingAll())
				_owner.sendMessage(_owner.getSysString(10_016));
		}

		_owner.revalidateZone(true);

		RelationManager.getInstance().notifyFriends(_owner, true);
		AutoFarmManager.getInstance().onPlayerLogin(_owner);
		PlayerListenerManager.getInstance().notifyPlayerEnter(_owner);
		QuestKillState.load(_owner);

		// Show language menu after a short delay for non-GM players.
		ThreadPool.schedule(() ->
		{
			if (_owner.isOnline() && !_owner.isGM() && TranslatorState.isApiActive())
				TranslatorState.showLanguageMenu(_owner);
		}, 3000);
	}

	// ========== Delete Me ==========

	/**
	 * Full player deletion lifecycle: remove from world, event cleanup, save to DB.
	 * Delegates to {@link Player#deleteMe()} which calls {@code super.deleteMe()} internally.
	 */
	public void deleteMe()
	{
		_owner.deleteMe();
	}

	/**
	 * Internal cleanup logic for player disconnection.
	 * This method contains the full body of the old private {@code cleanup()} method.
	 * Can be called by Player during its deleteMe sequence.
	 */
	public synchronized void cleanup()
	{
		try
		{
			_owner.setOnlineStatus(false, true);

			_owner.abortAll(true);

			_owner.removeMeFromPartyMatch();

			if (_owner.isFlying())
				_owner.removeSkill(FrequentSkill.WYVERN_BREATH.getSkill().getId(), false);

			if (_owner.isMounted())
				_owner.dismount();
			else if (_owner.getSummon() != null)
				_owner.getSummon().unSummon(_owner);

			_owner.stopChargeTask();

			_owner.getPunishment().stopTask(true);

			WaterTaskManager.getInstance().remove(_owner);
			AttackStanceTaskManager.getInstance().remove(_owner);
			PvpFlagTaskManager.getInstance().remove(_owner, false);
			ShadowItemTaskManager.getInstance().remove(_owner);

			for (Quest quest : ScriptData.getInstance().getQuests())
				quest.cancelQuestTimers(_owner);

			_owner.forEachKnownType(Creature.class,
				creature -> creature.getFusionSkill() != null && creature.getFusionSkill().getTarget() == _owner,
				creature -> creature.getCast().stop());

			for (final AbstractEffect effect : _owner.getAllEffects())
			{
				if (effect.getSkill().isToggle())
				{
					effect.exit();
					continue;
				}

				switch (effect.getEffectType())
				{
					case SIGNET_GROUND, SIGNET_EFFECT:
						effect.exit();
						break;
				}
			}

			_owner.decayMe();

			if (_owner.getParty() != null)
				_owner.getParty().removePartyMember(_owner, MessageType.DISCONNECTED);

			if (OlympiadManager.getInstance().isRegistered(_owner) || _owner.getOlympiadGameId() != -1)
				OlympiadManager.getInstance().removeDisconnectedCompetitor(_owner);

			if (_owner.getClan() != null)
			{
				final ClanMember clanMember = _owner.getClan().getClanMember(_owner.getObjectId());
				if (clanMember != null)
					clanMember.setPlayerInstance(null);
			}

			if (_owner.getActiveRequester() != null)
			{
				_owner.setActiveRequester(null);
				_owner.cancelActiveTrade();
			}

			if (_owner.isGM())
				AdminData.getInstance().deleteGm(_owner);

			if (_owner.isInObserverMode())
				_owner.setXYZInvisible(_owner.getSavedLocation());

			CTFEvent.getInstance().onLogout(_owner);
			DMEvent.getInstance().onLogout(_owner);
			LMEvent.getInstance().onLogout(_owner);
			TvTEvent.getInstance().onLogout(_owner);

			_owner.getInventory().deleteMe();

			_owner.clearWarehouse();

			_owner.clearFreight();
			_owner.clearDepositedFreight();

			if (_owner.isCursedWeaponEquipped())
				CursedWeaponManager.getInstance().getCursedWeapon(_owner.getCursedWeaponEquippedId()).setPlayer(null);

			if (_owner.getClan() != null)
				_owner.getClan().broadcastToMembersExcept(_owner, new PledgeShowMemberListUpdate(_owner));

			if (_owner.isSeated())
			{
				final WorldObject object = World.getInstance().getObject(_owner.getThroneId());
				if (object instanceof StaticObject staticObject)
					staticObject.setBusy(false);
			}

			RelationManager.getInstance().notifyFriends(_owner, false);

			AutoFarmManager.getInstance().stopPlayer(_owner, null);

			World.getInstance().removePlayer(_owner);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't disconnect correctly the player.", e);
		}
	}
}
