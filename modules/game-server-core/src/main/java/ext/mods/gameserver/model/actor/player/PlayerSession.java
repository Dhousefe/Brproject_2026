package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.GameClient;

/**
 * Online / time / client session surface for a {@link Player}.
 */
public final class PlayerSession
{
	private final Player _owner;
	
	private GameClient _client;
	private boolean _isOnline;
	private long _onlineTime;
	private long _onlineBeginTime;
	private long _lastAccess;
	private long _uptime;
	private long _deleteTimer;
	
	public PlayerSession(Player owner)
	{
		_owner = owner;
	}
	
	public GameClient getClient()
	{
		return _client;
	}
	
	public void setClient(GameClient client)
	{
		_client = client;
	}
	
	public boolean isOnline()
	{
		return _isOnline;
	}
	
	public void setOnline(boolean isOnline)
	{
		_isOnline = isOnline;
	}
	
	public long getOnlineTime()
	{
		return _onlineTime;
	}
	
	public void setOnlineTime(long time)
	{
		_onlineTime = time;
		_onlineBeginTime = System.currentTimeMillis();
	}
	
	public long getOnlineBeginTime()
	{
		return _onlineBeginTime;
	}
	
	public long getLastAccess()
	{
		return _lastAccess;
	}
	
	public void setLastAccess(long lastAccess)
	{
		_lastAccess = lastAccess;
	}
	
	public long getUptime()
	{
		return System.currentTimeMillis() - _uptime;
	}
	
	public void setUptime(long time)
	{
		_uptime = time;
	}
	
	public long getDeleteTimer()
	{
		return _deleteTimer;
	}
	
	public void setDeleteTimer(long time)
	{
		_deleteTimer = time;
	}
}
