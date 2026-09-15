package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/**
 * Offline shop start time + offline farm flag for a {@link Player} (att-ver-3.0 batch).
 */
public final class PlayerOffline
{
	private final Player _owner;
	
	private long _offlineShopStart;
	private boolean _offlineFarm;
	
	public PlayerOffline(Player owner)
	{
		_owner = owner;
	}
	
	public long getOfflineStartTime()
	{
		return _offlineShopStart;
	}
	
	public void setOfflineStartTime(long time)
	{
		_offlineShopStart = time;
	}
	
	public boolean isOfflineFarm()
	{
		return _offlineFarm;
	}
	
	public void setOfflineFarm(boolean offlineFarm)
	{
		_offlineFarm = offlineFarm;
	}
	
	public void startOfflineFarm()
	{
		setOfflineFarm(true);
	}
	
	public void stopOfflineFarm()
	{
		setOfflineFarm(false);
	}
}
