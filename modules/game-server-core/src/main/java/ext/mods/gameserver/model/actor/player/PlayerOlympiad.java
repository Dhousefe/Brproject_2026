package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.enums.MessageType;
import ext.mods.gameserver.enums.SpawnType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.olympiad.OlympiadGameManager;
import ext.mods.gameserver.model.olympiad.OlympiadGameTask;
import ext.mods.gameserver.network.serverpackets.ExOlympiadMode;

/**
 * Olympiad mode / side / game id + observer flow for a {@link Player} (att-ver-3.0 onda 5).
 */
public final class PlayerOlympiad
{
	private final Player _owner;
	
	private boolean _isInOlympiadMode;
	private boolean _isInOlympiadStart;
	private int _olympiadGameId = -1;
	private int _olympiadSide = -1;
	
	public PlayerOlympiad(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isInOlympiadMode()
	{
		return _isInOlympiadMode;
	}
	
	public void setOlympiadMode(boolean b)
	{
		_isInOlympiadMode = b;
	}
	
	public boolean isOlympiadStart()
	{
		return _isInOlympiadStart;
	}
	
	public void setOlympiadStart(boolean b)
	{
		_isInOlympiadStart = b;
	}
	
	public boolean isOlympiadProtection()
	{
		return isInOlympiadMode();
	}
	
	public int getOlympiadSide()
	{
		return _olympiadSide;
	}
	
	public void setOlympiadSide(int i)
	{
		_olympiadSide = i;
	}
	
	public int getOlympiadGameId()
	{
		return _olympiadGameId;
	}
	
	public void setOlympiadGameId(int id)
	{
		_olympiadGameId = id;
	}
	
	public void enterOlympiadObserverMode(int id)
	{
		final OlympiadGameTask task = OlympiadGameManager.getInstance().getOlympiadTask(id);
		if (task == null)
			return;
		
		_owner.dropAllSummons();
		
		if (_owner.getParty() != null)
			_owner.getParty().removePartyMember(_owner, MessageType.EXPELLED);
		
		_olympiadGameId = id;
		
		_owner.standUp();
		
		if (!_owner.isInObserverMode())
			_owner.getSavedLocation().set(_owner.getPosition());
		
		_owner.setTarget(null);
		_owner.setInvul(true);
		_owner.getAppearance().setVisible(false);
		
		_owner.teleportTo(task.getZone().getSpawns(SpawnType.NORMAL).get(2), 0);
		_owner.sendPacket(new ExOlympiadMode(3));
	}
	
	public void leaveOlympiadObserverMode()
	{
		if (_olympiadGameId == -1)
			return;
		
		_olympiadGameId = -1;
		
		_owner.getAI().tryToIdle();
		
		_owner.setTarget(null);
		if (!_owner.isGM())
			_owner.getAppearance().setVisible(true);
		_owner.setInvul(false);
		
		_owner.sendPacket(new ExOlympiadMode(0));
		_owner.teleportTo(_owner.getSavedLocation(), 0);
		
		_owner.getSavedLocation().clean();
	}
}
