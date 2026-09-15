package ext.mods.gameserver.model.actor.player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ext.mods.gameserver.model.actor.Player;

/**
 * Active auto soulshot item ids for a {@link Player}.
 */
public final class PlayerSoulShot
{
	private final Player _owner;
	private final Set<Integer> _activeSoulShots = ConcurrentHashMap.newKeySet(1);
	
	public PlayerSoulShot(Player owner)
	{
		_owner = owner;
	}
	
	public void addAutoSoulShot(int itemId)
	{
		_activeSoulShots.add(itemId);
	}
	
	public boolean removeAutoSoulShot(int itemId)
	{
		return _activeSoulShots.remove(itemId);
	}
	
	public Set<Integer> getAutoSoulShot()
	{
		return _activeSoulShots;
	}
	
	public boolean contains(int itemId)
	{
		return _activeSoulShots.contains(itemId);
	}
	
	public void clear()
	{
		_activeSoulShots.clear();
	}
	
	public boolean isEmpty()
	{
		return _activeSoulShots.isEmpty();
	}
	
	public Set<Integer> raw()
	{
		return _activeSoulShots;
	}
}
