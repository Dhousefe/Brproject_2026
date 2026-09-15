package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.Door;
import ext.mods.gameserver.skills.L2Skill;

/**
 * Marry / summon-friend / gate open request surface for a {@link Player}.
 */
public final class PlayerSocial
{
	private final Player _owner;
	
	private boolean _isUnderMarryRequest;
	private int _requesterId;
	private Player _summonTargetRequest;
	private L2Skill _summonSkillRequest;
	private Door _requestedGate;
	
	public PlayerSocial(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isUnderMarryRequest()
	{
		return _isUnderMarryRequest;
	}
	
	public void setUnderMarryRequest(boolean state)
	{
		_isUnderMarryRequest = state;
	}
	
	public int getRequesterId()
	{
		return _requesterId;
	}
	
	public void setRequesterId(int requesterId)
	{
		_requesterId = requesterId;
	}
	
	public Player getSummonTargetRequest()
	{
		return _summonTargetRequest;
	}
	
	public void setSummonTargetRequest(Player target)
	{
		_summonTargetRequest = target;
	}
	
	public L2Skill getSummonSkillRequest()
	{
		return _summonSkillRequest;
	}
	
	public void setSummonSkillRequest(L2Skill skill)
	{
		_summonSkillRequest = skill;
	}
	
	public Door getRequestedGate()
	{
		return _requestedGate;
	}
	
	public void setRequestedGate(Door door)
	{
		_requestedGate = door;
	}
}
