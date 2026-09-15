package ext.mods.gameserver.model.actor.player;

import java.util.Map;

import ext.mods.gameserver.model.actor.Creature;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.npc.RewardInfo;

/**
 * EXP / SP / leveling surface for a {@link Player} (att-ver-3.0 onda 6).
 * Business logic lives on the PlayerStatus object; this component only
 * applies the stop-exp gate for exp gains.
 */
public final class PlayerExpLeveling
{
	private final Player _owner;

	public PlayerExpLeveling(Player owner)
	{
		_owner = owner;
	}

	public void addExpAndSp(long addToExp, int addToSp)
	{
		if (_owner.getStopExp())
			_owner.getStatus().addExpAndSp(addToExp, addToSp);
		else
			_owner.getStatus().addExpAndSp(0, addToSp);
	}

	public void addExpAndSp(long addToExp, int addToSp, Map<Creature, RewardInfo> rewards)
	{
		if (_owner.getStopExp())
			_owner.getStatus().addExpAndSp(addToExp, addToSp, rewards);
		else
			_owner.getStatus().addExpAndSp(0, addToSp, rewards);
	}

	public void removeExpAndSp(long removeExp, int removeSp)
	{
		_owner.getStatus().removeExpAndSp(removeExp, removeSp);
	}
}
