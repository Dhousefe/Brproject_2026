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
package ext.mods.gameserver.model.actor.container.player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import ext.mods.commons.logging.CLogger;

import ext.mods.Config;
import ext.mods.gameserver.custom.data.MissionData;
import ext.mods.gameserver.data.repository.MissionRecord;
import ext.mods.gameserver.data.service.PlayerAuxiliaryPersistenceService;
import ext.mods.gameserver.enums.SayType;
import ext.mods.gameserver.enums.actors.MissionType;
import ext.mods.gameserver.model.Mission;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.holder.IntIntHolder;
import ext.mods.gameserver.network.serverpackets.CreatureSay;
import ext.mods.gameserver.network.serverpackets.MagicSkillUse;
import ext.mods.config.ConfigPlayers;
import ext.mods.config.ConfigProject;

public class MissionList
{
	private static final CLogger LOGGER = new CLogger(MissionList.class.getName());
	
	private final Player _player;
	private Map<MissionType, IntIntHolder> _entries = new HashMap<>();
	
	public MissionList(Player player)
	{
		_player = player;
	}
	
	public void restore()
	{
		if (!ConfigProject.ENABLE_MISSION)
			return;
		
		try
		{
			for (MissionRecord record : PlayerAuxiliaryPersistenceService.loadMissions(_player.getObjectId()))
			{
				_entries.put(MissionType.valueOf(record.type()), new IntIntHolder(record.level(), record.value()));
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load mission list for player:", e, _player.getName());
		}
	}
	
	public void store()
	{
		if (!ConfigProject.ENABLE_MISSION)
			return;
		
		try
		{
			final List<MissionRecord> records = new java.util.ArrayList<>();
			for (Entry<MissionType, IntIntHolder> mission : _entries.entrySet())
			{
				if (mission.getValue().getId() == 0 && mission.getValue().getValue() == 0)
					continue;
				
				records.add(new MissionRecord(String.valueOf(mission.getKey()), mission.getValue().getId(), mission.getValue().getValue()));
			}
			PlayerAuxiliaryPersistenceService.saveMissions(_player.getObjectId(), records);
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load mission list for player: {} (Id: {}). Exception: ", _player.getName(), _player.getObjectId(), e);
		}
	}
	
	/**
	 * @param type : The {@link MissionType}.
	 * @return The mission information.
	 */
	public IntIntHolder getMission(MissionType type)
	{
		if (!_entries.containsKey(type))
			_entries.put(type, new IntIntHolder(0, 0));
		
		return _entries.get(type);
	}
	
	/**
	 * @param type : The {@link MissionType} to increase value by 1 with reset on level up for all the party.
	 */
	public void updateParty(MissionType type)
	{
		if (!ConfigProject.ENABLE_MISSION)
			return;
		
		if (_player.getParty() != null)
			_player.getKnownTypeInRadius(Player.class, ConfigPlayers.PARTY_RANGE, x -> _player.getParty().containsPlayer(x)).forEach(x -> x.getMissions().update(type));
		else
			update(type);
	}
	
	/**
	 * @param type : The {@link MissionType} to increase value by 1 with reset on level up.
	 */
	public void update(MissionType type)
	{
		if (!ConfigProject.ENABLE_MISSION)
			return;
		
		set(type, 1, true, true);
	}
	
	/**
	 * @param type : The {@link MissionType}.
	 * @param value : The new value for the mission information.
	 * @param increaseValue : If true increase the current mission value by new value else it replace it.
	 * @param resetValue : If true reset the current mission value else keep the previews level value.
	 */
	public void set(MissionType type, int value, boolean increaseValue, boolean resetValue)
	{
		if (!ConfigProject.ENABLE_MISSION)
			return;
		
		final List<Mission> missions = MissionData.getInstance().getMission(type);
		if (type == null || missions == null || missions.isEmpty())
			return;
		
		final IntIntHolder mission = _entries.containsKey(type) ? _entries.get(type) : new IntIntHolder(0, value);
		if (missions.size() < mission.getId())
			return;
		
		final Mission missionData = MissionData.getInstance().getMissionByLevel(type, mission.getId() + 1);
		if (missionData == null || missionData.getLevel() == mission.getId())
			return;
		
		mission.setValue(increaseValue ? mission.getValue() + value : value);
		
		if (missionData.getRequired() <= mission.getValue())
		{
			if (missionData.getRewards() != null && !missionData.getRewards().isEmpty())
				missionData.getRewards().forEach(reward -> _player.addItem(reward.getId(), reward.getValue(), true));
			
			mission.setId(mission.getId() + 1);
			mission.setValue(resetValue ? 0 : mission.getValue());
			
			_player.broadcastPacket(new MagicSkillUse(_player, 5103, 1, 1000, 0));
			_player.sendPacket(new CreatureSay(SayType.PARTY, "Achievements", "Lv " + missionData.getLevel() + " " + missionData.getName() + " mission complete."));
		}
		_entries.put(type, mission);
	}
	
	/**
	 * @return The available {@link MissionType} for this {@link Player}.
	 */
	public List<MissionType> getAvailableTypes()
	{
		final Map<MissionType, List<Mission>> missions = MissionData.getInstance().getMissions();
		if (missions == null || missions.isEmpty())
			return Collections.emptyList();
		
		return missions.keySet().stream().filter(type -> isAvailable(type)).collect(Collectors.toList());
	}
	
	/**
	 * @param type : The {@link MissionType}.
	 * @return True if the type is available for this {@link Player}.
	 */
	private boolean isAvailable(MissionType type)
	{
		switch (type)
		{
			case CASTLE:
			case CLAN_LEVEL_UP:
				return _player.isClanLeader();
			
			case LEADER:
				return _player.getClan() == null;
			
			case SPOIL:
				return _player.getSkill(254) != null;
			
			case ACADEMY:
				return _player.getClassId().getLevel() < 2;
		}
		return true;
	}
}
