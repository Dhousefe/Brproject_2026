package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.enums.PunishmentType;
import ext.mods.gameserver.enums.TeamType;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.duels.DuelState;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.SystemMessage;

/**
 * Duel state surface for a {@link Player} (att-ver-3.0 onda 8).
 */
public final class PlayerDuel
{
	private final Player _owner;
	
	private DuelState _duelState = DuelState.NO_DUEL;
	private int _duelId;
	private SystemMessageId _noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;
	
	public PlayerDuel(Player owner)
	{
		_owner = owner;
	}
	
	public boolean isInDuel()
	{
		return _duelId > 0;
	}
	
	public int getDuelId()
	{
		return _duelId;
	}
	
	public void setDuelState(DuelState state)
	{
		_duelState = state;
	}
	
	public DuelState getDuelState()
	{
		return _duelState;
	}
	
	/**
	 * Sets up the duel state using a non 0 duelId.
	 * @param duelId 0=not in a duel
	 */
	public void setInDuel(int duelId)
	{
		if (duelId > 0)
		{
			_duelState = DuelState.ON_COUNTDOWN;
			_duelId = duelId;
		}
		else
		{
			if (_duelState == DuelState.DEAD)
			{
				_owner.enableAllSkills();
				_owner.getStatus().startHpMpRegeneration();
			}
			_duelState = DuelState.NO_DUEL;
			_duelId = 0;
		}
	}
	
	public SystemMessage getNoDuelReason()
	{
		final SystemMessage sm = SystemMessage.getSystemMessage(_noDuelReason).addCharName(_owner);
		
		_noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL;
		
		return sm;
	}
	
	/**
	 * @return true if the player might join/start a duel.
	 */
	public boolean canDuel()
	{
		if (_owner.isInCombat() || _owner.getPunishment().getType() == PunishmentType.JAIL)
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_CURRENTLY_ENGAGED_IN_BATTLE;
		else if (_owner.isDead() || _owner.isAlikeDead() || _owner.getStatus().getHpRatio() < 0.5 || _owner.getStatus().getMpRatio() < 0.5)
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_HP_OR_MP_IS_BELOW_50_PERCENT;
		else if (isInDuel())
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_ALREADY_ENGAGED_IN_A_DUEL;
		else if (_owner.isInOlympiadMode())
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_PARTICIPATING_IN_THE_OLYMPIAD;
		else if (_owner.isCursedWeaponEquipped() || _owner.getKarma() != 0 || _owner.getPvpFlag() > 0)
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_IN_A_CHAOTIC_STATE;
		else if (_owner.isOperating())
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_CURRENTLY_ENGAGED_IN_A_PRIVATE_STORE_OR_MANUFACTURE;
		else if (_owner.isMounted() || _owner.getBoatInfo().isInBoat())
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_CURRENTLY_RIDING_A_BOAT_WYVERN_OR_STRIDER;
		else if (_owner.isFishing())
			_noDuelReason = SystemMessageId.S1_CANNOT_DUEL_BECAUSE_S1_IS_CURRENTLY_FISHING;
		else if (_owner.isInsideZone(ZoneId.PVP) || _owner.isInsideZone(ZoneId.PEACE) || _owner.isInsideZone(ZoneId.SIEGE) || _owner.isInsideZone(ZoneId.WATER) || _owner.isInsideZone(ZoneId.NO_RESTART))
			_noDuelReason = SystemMessageId.S1_CANNOT_MAKE_A_CHALLANGE_TO_A_DUEL_BECAUSE_S1_IS_CURRENTLY_IN_A_DUEL_PROHIBITED_AREA;
		else
			return true;
		
		return false;
	}
	
	public void resetDuelState()
	{
		setInDuel(0);
		_owner.setTeam(TeamType.NONE);
		_owner.broadcastUserInfo();
		
		if (_owner.getSummon() != null)
			_owner.getSummon().updateAbnormalEffect();
	}
	
	public void prepareToDuel(TeamType type)
	{
		_owner.cancelActiveEnchant();
		_owner.cancelActiveTrade();
		
		setDuelState(DuelState.DUELLING);
		_owner.setTeam(type);
		_owner.broadcastUserInfo();
		
		if (_owner.getSummon() != null)
			_owner.getSummon().updateAbnormalEffect();
	}
}
