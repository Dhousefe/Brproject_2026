package ext.mods.gameserver.model.actor.player;

import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.location.Location;
import ext.mods.gameserver.network.serverpackets.ExServerPrimitive;

/**
 * Lightweight UI/session prefs for a {@link Player}: ACP, commands, event points, minigame, mail, debug.
 */
public final class PlayerUiPrefs
{
	private final Player _owner;
	
	private int _hpPotionPercent = 50;
	private int _mpPotionPercent = 50;
	private String _lastCommand;
	private int _eventPoints;
	private int _mailPosition;
	private final int[] _loto = new int[5];
	private final int[] _race = new int[2];
	private final Map<String, ExServerPrimitive> _debug = new HashMap<>();
	private Location _enterWorld;
	
	public PlayerUiPrefs(Player owner)
	{
		_owner = owner;
	}
	
	public Location getEnterWorld()
	{
		return _enterWorld;
	}
	
	public void setEnterWorld(Location enterWorld)
	{
		_enterWorld = enterWorld;
	}
	
	public int getHpPotionPercentage()
	{
		return _hpPotionPercent;
	}
	
	public void setHpPotionPercentage(int percent)
	{
		_hpPotionPercent = Math.max(0, Math.min(100, percent));
	}
	
	public int getMpPotionPercentage()
	{
		return _mpPotionPercent;
	}
	
	public void setMpPotionPercentage(int percent)
	{
		_mpPotionPercent = Math.max(0, Math.min(100, percent));
	}
	
	public String getLastCommand()
	{
		return _lastCommand;
	}
	
	public void setLastCommand(String lastCommand)
	{
		_lastCommand = lastCommand;
	}
	
	public int getPointScore()
	{
		return _eventPoints;
	}
	
	public void increasePointScore()
	{
		_eventPoints++;
	}
	
	public void clearPoints()
	{
		_eventPoints = 0;
	}
	
	public int getMailPosition()
	{
		return _mailPosition;
	}
	
	public void setMailPosition(int mailPosition)
	{
		_mailPosition = mailPosition;
	}
	
	public int getLoto(int i)
	{
		return _loto[i];
	}
	
	public void setLoto(int i, int val)
	{
		_loto[i] = val;
	}
	
	public int getRace(int i)
	{
		return _race[i];
	}
	
	public void setRace(int i, int val)
	{
		_race[i] = val;
	}
	
	public ExServerPrimitive getDebugPacket(String name)
	{
		synchronized (_debug)
		{
			return _debug.computeIfAbsent(name, p -> new ExServerPrimitive(name, _enterWorld));
		}
	}
	
	public void clearDebugPackets()
	{
		final List<ExServerPrimitive> snapshot;
		synchronized (_debug)
		{
			snapshot = new ArrayList<>(_debug.values());
			_debug.clear();
		}
		
		snapshot.forEach(esp ->
		{
			esp.reset();
			esp.sendTo(_owner);
		});
	}
}

