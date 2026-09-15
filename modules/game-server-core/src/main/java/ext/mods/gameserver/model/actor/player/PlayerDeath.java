package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/**
 * Death-penalty / fake-death / revive request surface for a {@link Player}.
 */
public final class PlayerDeath
{
	private final Player _owner;
	
	private int _deathPenaltyBuffLevel;
	private boolean _isFakeDeath;
	private long _recentFakeDeathEndTime;
	private int _reviveRequested;
	private double _revivePower;
	private boolean _revivePet;
	
	public PlayerDeath(Player owner)
	{
		_owner = owner;
	}
	
	public int getDeathPenaltyBuffLevel()
	{
		return _deathPenaltyBuffLevel;
	}
	
	public void setDeathPenaltyBuffLevel(int level)
	{
		_deathPenaltyBuffLevel = level;
	}
	
	public boolean isFakeDeath()
	{
		return _isFakeDeath;
	}
	
	public void setFakeDeath(boolean fakeDeath)
	{
		_isFakeDeath = fakeDeath;
	}
	
	public long getRecentFakeDeathEndTime()
	{
		return _recentFakeDeathEndTime;
	}
	
	public void setRecentFakeDeathEndTime(long time)
	{
		_recentFakeDeathEndTime = time;
	}
	
	public int getReviveRequested()
	{
		return _reviveRequested;
	}
	
	public void setReviveRequested(int reviveRequested)
	{
		_reviveRequested = reviveRequested;
	}
	
	public double getRevivePower()
	{
		return _revivePower;
	}
	
	public void setRevivePower(double revivePower)
	{
		_revivePower = revivePower;
	}
	
	public boolean isRevivePet()
	{
		return _revivePet;
	}
	
	public void setRevivePet(boolean revivePet)
	{
		_revivePet = revivePet;
	}
}
