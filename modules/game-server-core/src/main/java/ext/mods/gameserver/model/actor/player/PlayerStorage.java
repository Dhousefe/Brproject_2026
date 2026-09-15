package ext.mods.gameserver.model.actor.player;

import java.util.ArrayList;
import java.util.List;

import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.itemcontainer.ItemContainer;
import ext.mods.gameserver.model.itemcontainer.PcFreight;
import ext.mods.gameserver.model.itemcontainer.PcWarehouse;

/**
 * Warehouse / freight storage for a {@link Player} (core trade infrastructure).
 */
public final class PlayerStorage
{
	private final Player _owner;
	
	private PcWarehouse _warehouse;
	private PcFreight _freight;
	private final List<PcFreight> _depositedFreight = new ArrayList<>();
	private ItemContainer _activeWarehouse;
	
	public PlayerStorage(Player owner)
	{
		_owner = owner;
	}
	
	public Player getOwner()
	{
		return _owner;
	}
	
	public PcWarehouse getWarehouse()
	{
		if (_warehouse == null)
		{
			_warehouse = new PcWarehouse(_owner);
			_warehouse.restore();
		}
		return _warehouse;
	}
	
	public void clearWarehouse()
	{
		if (_warehouse != null)
			_warehouse.deleteMe();
		_warehouse = null;
	}
	
	public PcFreight getFreight()
	{
		if (_freight == null)
		{
			_freight = new PcFreight(_owner);
			_freight.restore();
		}
		return _freight;
	}
	
	public void clearFreight()
	{
		if (_freight != null)
			_freight.deleteMe();
		_freight = null;
	}
	
	public PcFreight getDepositedFreight(int objectId)
	{
		for (final PcFreight freight : _depositedFreight)
		{
			if (freight != null && freight.getOwnerId() == objectId)
				return freight;
		}
		
		final PcFreight freight = new PcFreight(null);
		freight.doQuickRestore(objectId);
		_depositedFreight.add(freight);
		return freight;
	}
	
	public void clearDepositedFreight()
	{
		_depositedFreight.forEach(PcFreight::deleteMe);
		_depositedFreight.clear();
	}
	
	public ItemContainer getActiveWarehouse()
	{
		return _activeWarehouse;
	}
	
	public void setActiveWarehouse(ItemContainer warehouse)
	{
		_activeWarehouse = warehouse;
	}
}
