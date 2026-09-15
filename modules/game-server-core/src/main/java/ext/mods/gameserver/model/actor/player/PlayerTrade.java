package ext.mods.gameserver.model.actor.player;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.trade.TradeList;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.SendTradeDone;
import ext.mods.gameserver.network.serverpackets.SystemMessage;
import ext.mods.gameserver.network.serverpackets.TradePressOtherOk;
import ext.mods.gameserver.network.serverpackets.TradePressOwnOk;
import ext.mods.gameserver.network.serverpackets.TradeStart;

/**
 * Peer-to-peer trade session for a {@link Player}.
 */
public final class PlayerTrade
{
	private final Player _owner;
	
	private TradeList _activeTradeList;
	
	public PlayerTrade(Player owner)
	{
		_owner = owner;
	}
	
	public TradeList getActiveTradeList()
	{
		return _activeTradeList;
	}
	
	public void setActiveTradeList(TradeList tradeList)
	{
		_activeTradeList = tradeList;
	}
	
	public boolean hasActiveTrade()
	{
		return _activeTradeList != null;
	}
	
	public void onTradeStart(Player partner)
	{
		_activeTradeList = new TradeList(_owner);
		_activeTradeList.setPartner(partner);
		
		_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.BEGIN_TRADE_WITH_S1).addString(partner.getName()));
		_owner.sendPacket(new TradeStart(_owner));
	}
	
	public void onTradeConfirm(Player partner)
	{
		_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CONFIRMED_TRADE).addString(partner.getName()));
		
		partner.sendPacket(TradePressOwnOk.STATIC_PACKET);
		_owner.sendPacket(TradePressOtherOk.STATIC_PACKET);
	}
	
	public void onTradeCancel(Player partner)
	{
		if (_activeTradeList == null)
			return;
		
		_activeTradeList.lock();
		_activeTradeList = null;
		
		_owner.sendPacket(SendTradeDone.FAIL_STATIC_PACKET);
		_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CANCELED_TRADE).addString(partner.getName()));
	}
	
	public void onTradeFinish(boolean isSuccessful)
	{
		_activeTradeList = null;
		
		if (isSuccessful)
		{
			_owner.sendPacket(SendTradeDone.SUCCESS_STATIC_PACKET);
			_owner.sendPacket(SystemMessageId.TRADE_SUCCESSFUL);
		}
		else
		{
			_owner.sendPacket(SendTradeDone.FAIL_STATIC_PACKET);
			_owner.sendPacket(SystemMessageId.EXCHANGE_HAS_ENDED);
		}
	}
	
	public void startTrade(Player partner)
	{
		onTradeStart(partner);
		partner.getTrade().onTradeStart(_owner);
	}
	
	public void cancelActiveTrade()
	{
		if (_activeTradeList == null)
			return;
		
		final Player partner = _activeTradeList.getPartner();
		if (partner != null)
			partner.getTrade().onTradeCancel(_owner);
		
		onTradeCancel(_owner);
	}
}
