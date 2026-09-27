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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import ext.mods.commons.logging.CLogger;
import ext.mods.gameserver.data.adapter.JdbcQuestStore;
import ext.mods.gameserver.data.repository.QuestStore;
import ext.mods.gameserver.data.xml.ScriptData;
import ext.mods.gameserver.enums.EventHandler;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.WorldObject;
import ext.mods.gameserver.model.actor.Npc;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.scripting.Quest;
import ext.mods.gameserver.scripting.QuestState;

public final class QuestList extends ArrayList<QuestState>
{
	private static final CLogger LOGGER = new CLogger(QuestList.class.getName());
	
	private static final long serialVersionUID = 1L;
	
	private final Player _player;
	private final QuestStore _store;
	
	private int _lastQuestNpcObjectId;
	
	public QuestList(Player player)
	{
		this(player, new JdbcQuestStore());
	}

	public QuestList(Player player, QuestStore store)
	{
		_player = player;
		_store = store;
	}
	
	public int getLastQuestNpcObjectId()
	{
		return _lastQuestNpcObjectId;
	}
	
	public void setLastQuestNpcObjectId(int objectId)
	{
		_lastQuestNpcObjectId = objectId;
	}
	
	/**
	 * @param name : The name of the {@link Quest}.
	 * @return The {@link QuestState} corresponding to the name, or null if not found.
	 */
	public QuestState getQuestState(String name)
	{
		return stream().filter(qs -> name.equals(qs.getQuest().getName())).findFirst().orElse(null);
	}
	
	/**
	 * Note: Only real quests are evaluated (they have id > 0).
	 * @param id : The id of the {@link Quest}.
	 * @return The {@link QuestState} corresponding to the quest id, or null if not found.
	 */
	public QuestState getQuestState(int id)
	{
		return stream().filter(qs -> id == qs.getQuest().getQuestId()).findFirst().orElse(null);
	}
	
	/**
	 * @param completed : If true, include completed quests to the {@link List}.
	 * @return A {@link List} of started and eventually completed {@link Quest}s.
	 */
	public List<QuestState> getAllQuests(boolean completed)
	{
		return stream().filter(qs -> qs.getQuest().isRealQuest() && (qs.isStarted() || (qs.isCompleted() && completed))).toList();
	}
	
	/**
	 * @param predicate : The {@link Predicate} defining {@link Quest} matching condition.
	 * @return The {@link List} of quests and scripts matching given {@link Predicate}.
	 */
	public List<Quest> getQuests(Predicate<Quest> predicate)
	{
		return stream().map(QuestState::getQuest).filter(predicate).toList();
	}
	
	/**
	 * Restore {@link QuestState}s of this {@link Player} from the database.
	 */
	public void restore()
	{
		try
		{
			for (QuestStore.VariableRecord record : _store.loadVariables(_player.getObjectId()))
			{
				final String questName = record.questName();
				final Quest quest = ScriptData.getInstance().getQuest(questName);
				if (quest == null)
				{
					LOGGER.warn("Unknown quest {} for player {}.", questName, _player.getName());
					continue;
				}

				QuestState qs = getQuestState(questName);
				if (qs == null)
					qs = new QuestState(_player, quest, _store);

				qs.loadVariable(record.variable(), record.value());
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore quests.", e);
		}
	}
	
	public void processQuestEvent(String questName, String event)
	{
		final Quest quest = ScriptData.getInstance().getQuest(questName);
		if (quest == null)
			return;
		
		final WorldObject object = World.getInstance().getObject(getLastQuestNpcObjectId());
		if (!(object instanceof Npc npc) || !_player.isIn3DRadius(npc, Npc.INTERACTION_DISTANCE))
			return;
		
		for (Quest script : npc.getTemplate().getEventQuests(EventHandler.TALKED))
		{
			if (!script.equals(quest))
				continue;
			
			quest.notifyEvent(event, npc, _player);
			break;
		}
	}
}
