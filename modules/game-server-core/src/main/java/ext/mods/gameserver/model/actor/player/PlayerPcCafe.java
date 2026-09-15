package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.enums.PcCafeConsumeType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.ExPCCafePointInfo;
import ext.mods.gameserver.network.serverpackets.SystemMessage;

/**
 * PC Cafe points stored in player memos (att-ver-3.0 batch).
 */
public final class PlayerPcCafe
{
	private final Player _owner;
	
	public PlayerPcCafe(Player owner)
	{
		_owner = owner;
	}
	
	public int getPcCafePoints()
	{
		return _owner.getMemos().getInteger("cafe_points", 0);
	}
	
	public void increasePcCafePoints(int count)
	{
		increasePcCafePoints(count, false);
	}
	
	public void increasePcCafePoints(int count, boolean doubleAmount)
	{
		count = doubleAmount ? count * 2 : count;
		final int newAmount = Math.min(_owner.getMemos().getInteger("cafe_points", 0) + count, 200000);
		_owner.getMemos().set("cafe_points", newAmount);
		_owner.sendPacket(SystemMessage.getSystemMessage(doubleAmount ? SystemMessageId.ACQUIRED_S1_PCPOINT_DOUBLE : SystemMessageId.ACQUIRED_S1_PCPOINT).addNumber(count));
		_owner.sendPacket(new ExPCCafePointInfo(newAmount, count, doubleAmount ? PcCafeConsumeType.DOUBLE_ADD : PcCafeConsumeType.ADD));
	}
	
	public void decreasePcCafePoints(int count)
	{
		final int newAmount = Math.max(_owner.getMemos().getInteger("cafe_points", 0) - count, 0);
		_owner.getMemos().set("cafe_points", newAmount);
		_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.USING_S1_PCPOINT).addNumber(count));
		_owner.sendPacket(new ExPCCafePointInfo(newAmount, -count, PcCafeConsumeType.CONSUME));
	}
}
