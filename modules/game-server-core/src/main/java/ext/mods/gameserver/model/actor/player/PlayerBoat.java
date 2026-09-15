package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.container.player.BoatInfo;

/**
 * Boat boarding surface for a {@link Player} (att-ver-3.0 batch).
 * Logic remains in {@link BoatInfo}; this owns the instance.
 */
public final class PlayerBoat
{
	private final BoatInfo _boatInfo;
	
	public PlayerBoat(Player owner)
	{
		_boatInfo = new BoatInfo(owner);
	}
	
	public BoatInfo getBoatInfo()
	{
		return _boatInfo;
	}
	
	public boolean isInBoat()
	{
		return _boatInfo.isInBoat();
	}
}
