package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.data.manager.CastleManager;
import ext.mods.gameserver.enums.PrivilegeType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.model.residence.castle.Castle;

/**
 * Clan membership surface for a {@link Player} (att-ver-3.0 onda 6).
 * ClanTable / Clan business logic stays external.
 */
public final class PlayerClan
{
	private final Player _owner;
	
	private int _clanId;
	private Clan _clan;
	private int _apprentice;
	private int _sponsor;
	private long _clanJoinExpiryTime;
	private long _clanCreateExpiryTime;
	private int _pledgeClass;
	private int _pledgeType;
	private int _powerGrade;
	private int _lvlJoinedAcademy;
	
	public PlayerClan(Player owner)
	{
		_owner = owner;
	}
	
	public int getClanId()
	{
		return _clanId;
	}
	
	public Clan getClan()
	{
		return _clan;
	}
	
	public void setClan(Clan clan)
	{
		_clan = clan;
		_owner.setTitle("");
		
		if (clan == null)
		{
			_clanId = 0;
			_pledgeType = 0;
			_powerGrade = 0;
			_lvlJoinedAcademy = 0;
			_apprentice = 0;
			_sponsor = 0;
			return;
		}
		
		if (!clan.isMember(_owner.getObjectId()))
		{
			setClan(null);
			return;
		}
		
		_clanId = clan.getClanId();
	}
	
	public boolean isCastleLord(int castleId)
	{
		if (!isClanLeader())
			return false;
		
		final Castle castle = CastleManager.getInstance().getCastleByOwner(_clan);
		return castle != null && castle.getId() == castleId;
	}
	
	public boolean isClanLeader()
	{
		return _clan != null && _owner.getObjectId() == _clan.getLeaderId();
	}
	
	public int getClanCrestId()
	{
		return (_clan != null) ? _clan.getCrestId() : 0;
	}
	
	public int getClanCrestLargeId()
	{
		return (_clan != null) ? _clan.getCrestLargeId() : 0;
	}
	
	public long getClanJoinExpiryTime()
	{
		return _clanJoinExpiryTime;
	}
	
	public void setClanJoinExpiryTime(long time)
	{
		_clanJoinExpiryTime = time;
	}
	
	public long getClanCreateExpiryTime()
	{
		return _clanCreateExpiryTime;
	}
	
	public void setClanCreateExpiryTime(long time)
	{
		_clanCreateExpiryTime = time;
	}
	
	public int getPledgeClass()
	{
		return _pledgeClass;
	}
	
	public void setPledgeClass(int classId)
	{
		_pledgeClass = classId;
	}
	
	public int getPledgeType()
	{
		return _pledgeType;
	}
	
	public void setPledgeType(int typeId)
	{
		_pledgeType = typeId;
	}
	
	public int getApprentice()
	{
		return _apprentice;
	}
	
	public void setApprentice(int id)
	{
		_apprentice = id;
	}
	
	public int getSponsor()
	{
		return _sponsor;
	}
	
	public void setSponsor(int id)
	{
		_sponsor = id;
	}
	
	public int getPowerGrade()
	{
		return _powerGrade;
	}
	
	public void setPowerGrade(int power)
	{
		_powerGrade = power;
	}
	
	public void setLvlJoinedAcademy(int lvl)
	{
		_lvlJoinedAcademy = lvl;
	}
	
	public int getLvlJoinedAcademy()
	{
		return _lvlJoinedAcademy;
	}
	
	public boolean isAcademyMember()
	{
		return _lvlJoinedAcademy > 0;
	}
	
	public int getAllyId()
	{
		if (_clan == null)
			return 0;
		return _clan.getAllyId();
	}
	
	public int getAllyCrestId()
	{
		if (_clanId == 0)
			return 0;
		if (_clan.getAllyId() == 0)
			return 0;
		return _clan.getAllyCrestId();
	}
	
	public boolean hasClanPrivileges(PrivilegeType priv)
	{
		if (_clan == null)
			return false;
		
		if (_clan.getLeaderId() == _owner.getObjectId())
			return true;
		
		return (_clan.getPrivilegesByRank(getPowerGrade()) & priv.getMask()) != 0;
	}
	
	public int getClanPrivileges()
	{
		if (_clan == null)
			return PrivilegeType.NONE.getMask();
		
		if (_clan.getLeaderId() == _owner.getObjectId())
			return PrivilegeType.ALL.getMask();
		
		return _clan.getPrivilegesByRank(getPowerGrade());
	}
	
	public boolean hasClan()
	{
		return _clan != null;
	}
}
