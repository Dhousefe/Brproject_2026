package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.enums.StatusType;
import ext.mods.gameserver.enums.actors.MissionType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.RelationChanged;
import ext.mods.gameserver.network.serverpackets.StatusUpdate;
import ext.mods.gameserver.network.serverpackets.SystemMessage;
import ext.mods.gameserver.network.serverpackets.UserInfo;

/**
 * PvP / PK / karma / flag + siege involvement flags for a {@link Player}.
 */
public final class PlayerPvP
{
	private final Player _owner;
	
	private int _karma;
	private int _pvpKills;
	private int _pkKills;
	private byte _pvpFlag;
	private int _siegeState;
	private boolean _isIn7sDungeon;
	private int _alliedVarkaKetra;
	
	public PlayerPvP(Player owner)
	{
		_owner = owner;
	}
	
	public int getKarma()
	{
		return _karma;
	}
	
	public void setKarma(int karma)
	{
		if (_karma == karma)
			return;
		
		_karma = Math.max(0, karma);
		
		_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_KARMA_HAS_BEEN_CHANGED_TO_S1).addNumber(_karma));
		
		final StatusUpdate su = new StatusUpdate(_owner);
		su.addAttribute(StatusType.KARMA, _karma);
		_owner.sendPacket(su);
		
		if (!_owner.isCursedWeaponEquipped() && _owner.getMissions().getMission(MissionType.KARMA).getValue() < karma)
			_owner.getMissions().set(MissionType.KARMA, karma, false, false);
		
		_owner.sendPacket(new UserInfo(_owner));
		if (_owner.getSummon() != null)
			_owner.sendPacket(new RelationChanged(_owner.getSummon(), _owner.getRelation(_owner), false));
		
		_owner.broadcastRelationsChanges();
	}
	
	public int getPvpKills()
	{
		return _pvpKills;
	}
	
	public void setPvpKills(int pvpKills)
	{
		_pvpKills = pvpKills;
	}
	
	public int getPkKills()
	{
		return _pkKills;
	}
	
	public void setPkKills(int pkKills)
	{
		_pkKills = pkKills;
	}
	
	public byte getPvpFlag()
	{
		return _pvpFlag;
	}
	
	public void setPvpFlag(int pvpFlag)
	{
		_pvpFlag = (byte) pvpFlag;
	}
	
	public void updatePvPFlag(int value)
	{
		if (getPvpFlag() == value)
			return;
		
		setPvpFlag(value);
		_owner.sendPacket(new UserInfo(_owner));
		
		if (_owner.getSummon() != null)
			_owner.sendPacket(new RelationChanged(_owner.getSummon(), _owner.getRelation(_owner), false));
		
		_owner.broadcastRelationsChanges();
	}
	
	public int getSiegeState()
	{
		return _siegeState;
	}
	
	public void setSiegeState(int siegeState)
	{
		_siegeState = siegeState;
	}
	
	public boolean isIn7sDungeon()
	{
		return _isIn7sDungeon;
	}
	
	public void setIsIn7sDungeon(boolean isIn7sDungeon)
	{
		_isIn7sDungeon = isIn7sDungeon;
	}
	
	public int getAllianceWithVarkaKetra()
	{
		return _alliedVarkaKetra;
	}
	
	public void setAllianceWithVarkaKetra(int sideAndLvlOfAlliance)
	{
		_alliedVarkaKetra = sideAndLvlOfAlliance;
	}
}
