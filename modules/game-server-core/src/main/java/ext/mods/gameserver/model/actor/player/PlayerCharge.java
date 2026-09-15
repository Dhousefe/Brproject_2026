package ext.mods.gameserver.model.actor.player;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import ext.mods.commons.pool.ThreadPool;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.EtcStatusUpdate;
import ext.mods.gameserver.network.serverpackets.SystemMessage;

/**
 * Force / soul charge stack for a {@link Player} (att-ver-3.0 batch).
 */
public final class PlayerCharge
{
	private final Player _owner;
	private final AtomicInteger _charges = new AtomicInteger();
	private ScheduledFuture<?> _chargeTask;
	
	public PlayerCharge(Player owner)
	{
		_owner = owner;
	}
	
	public int getCharges()
	{
		return _charges.get();
	}
	
	public void increaseCharges(int count, int max)
	{
		if (_charges.get() >= max)
		{
			_owner.sendPacket(SystemMessageId.FORCE_MAXLEVEL_REACHED);
			return;
		}
		
		restartChargeTask();
		
		if (_charges.addAndGet(count) >= max)
		{
			_charges.set(max);
			_owner.sendPacket(SystemMessageId.FORCE_MAXLEVEL_REACHED);
		}
		else
			_owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FORCE_INCREASED_TO_S1).addNumber(_charges.get()));
		
		_owner.sendPacket(new EtcStatusUpdate(_owner));
	}
	
	public boolean decreaseCharges(int count)
	{
		if (_charges.get() < count)
			return false;
		
		if (_charges.addAndGet(-count) == 0)
			stopChargeTask();
		else
			restartChargeTask();
		
		_owner.sendPacket(new EtcStatusUpdate(_owner));
		return true;
	}
	
	public void clearCharges()
	{
		if (_charges.get() > 0)
		{
			_charges.set(0);
			_owner.sendPacket(new EtcStatusUpdate(_owner));
		}
	}
	
	/** Clear charges when switching subclass. */
	public void clearForClassChange()
	{
		_charges.set(0);
		stopChargeTask();
	}
	
	private void restartChargeTask()
	{
		if (_chargeTask != null)
		{
			_chargeTask.cancel(false);
			_chargeTask = null;
		}
		
		_chargeTask = ThreadPool.schedule(this::clearCharges, 600000);
	}
	
	public void stopChargeTask()
	{
		if (_chargeTask != null)
		{
			_chargeTask.cancel(false);
			_chargeTask = null;
		}
	}
}
