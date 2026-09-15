package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.List;

import ext.mods.gameserver.model.actor.Player;

/**
 * HTML bypass validation lists for a {@link Player}.
 */
public final class PlayerBypass
{
	private final Player _owner;
	private final List<String> _validBypass = new ArrayList<>();
	private final List<String> _validBypass2 = new ArrayList<>();
	
	public PlayerBypass(Player owner)
	{
		_owner = owner;
	}
	
	public synchronized void addBypass(String bypass)
	{
		if (bypass == null)
			return;
		
		_validBypass.add(bypass);
	}
	
	public synchronized void addBypass2(String bypass)
	{
		if (bypass == null)
			return;
		
		_validBypass2.add(bypass);
	}
	
	public synchronized boolean validateBypass(String cmd)
	{
		for (final String bp : _validBypass)
		{
			if (bp == null)
				continue;
			
			if (bp.equals(cmd))
				return true;
		}
		
		for (final String bp : _validBypass2)
		{
			if (bp == null)
				continue;
			
			if (cmd.startsWith(bp))
				return true;
		}
		
		return false;
	}
	
	public synchronized void clearBypass()
	{
		_validBypass.clear();
		_validBypass2.clear();
	}
}
