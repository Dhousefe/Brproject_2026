package ext.mods.gameserver.model.actor.player;

import java.util.concurrent.ScheduledFuture;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.config.ConfigPlayers;
import ext.mods.gameserver.model.actor.Player;

/**
 * Spawn protection + related tasks for a {@link Player}.
 */
public final class PlayerProtect
{
	private final Player _owner;
	private ScheduledFuture<?> _protectTask;
	private ScheduledFuture<?> _gmRehideTask;
	
	public PlayerProtect(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isSpawnProtected()
	{
		return _protectTask != null;
	}
	
	public void setSpawnProtection(boolean isActive)
	{
		if (isActive)
		{
			if (_protectTask == null)
				_protectTask = ThreadPool.schedule(() ->
				{
					setSpawnProtection(false);
					_owner.sendMessage(_owner.getSysString(10_011));
				}, ConfigPlayers.PLAYER_SPAWN_PROTECTION * 1000L);
		}
		else
		{
			if (_protectTask != null)
			{
				_protectTask.cancel(true);
				_protectTask = null;
			}
		}
		_owner.broadcastUserInfo();
	}
	
	public ScheduledFuture<?> getGmRehideTask()
	{
		return _gmRehideTask;
	}
	
	public void setGmRehideTask(ScheduledFuture<?> task)
	{
		_gmRehideTask = task;
	}
	
	public void cancelGmRehideTask()
	{
		if (_gmRehideTask != null)
		{
			_gmRehideTask.cancel(false);
			_gmRehideTask = null;
		}
	}
}
