package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;

/**
 * HWID cache for a {@link Player} (att-ver-3.0 batch).
 */
public final class PlayerHwid
{
	private final Player _owner;
	private String _hwid;
	
	public PlayerHwid(Player owner)
	{
		_owner = owner;
	}
	
	public String getHWid()
	{
		if (_owner.getClient() == null)
			return _hwid;
		
		_hwid = _owner.getClient().getHWID();
		return _hwid;
	}
	
	public void setHWid(String hwid)
	{
		_hwid = hwid;
	}
}
