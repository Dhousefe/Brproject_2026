package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.data.manager.FestivalOfDarknessManager;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.MissionList;
import ext.mods.gameserver.model.actor.container.player.QuestList;

/**
 * Quest, mission, lottery, race and festival of darkness helpers (att-ver-3.0 batch).
 */
public final class PlayerQuestMission
{
	private final Player _owner;

	public PlayerQuestMission(Player owner)
	{
		_owner = owner;
	}

	/**
	 * @return True if Player is a participant in the Festival of Darkness.
	 */
	public boolean isFestivalParticipant()
	{
		return FestivalOfDarknessManager.getInstance().isParticipant(_owner);
	}

	public int getLoto(int i)
	{
		return _owner.getUiPrefs().getLoto(i);
	}

	public void setLoto(int i, int val)
	{
		_owner.getUiPrefs().setLoto(i, val);
	}

	public int getRace(int i)
	{
		return _owner.getUiPrefs().getRace(i);
	}

	public void setRace(int i, int val)
	{
		_owner.getUiPrefs().setRace(i, val);
	}

	public QuestList getQuestList()
	{
		return _owner.getQuestList();
	}

	public MissionList getMissions()
	{
		return _owner.getMissions();
	}
}
