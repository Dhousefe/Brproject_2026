package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.enums.TeamType;
import ext.mods.gameserver.model.actor.Player;

/**
 * Temporary team color (events/duel) for a {@link Player}.
 */
public final class PlayerTeam
{
	private final Player _owner;
	private TeamType _team = TeamType.NONE;
	
	public PlayerTeam(Player owner)
	{
		_owner = owner;
	}
	
	public TeamType getTeam()
	{
		return _team;
	}
	
	public void setTeam(TeamType team)
	{
		_team = team;
	}
}
