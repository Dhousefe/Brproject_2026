package ext.mods.gameserver.model.actor.player;

import java.util.List;

import ext.mods.commons.cached.CachedDataValueString;
import ext.mods.config.ConfigOfflineShop;
import ext.mods.gameserver.enums.PrivateStoreType;
import ext.mods.gameserver.enums.ZoneId;
import ext.mods.gameserver.enums.actors.OperateType;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.craft.ManufactureList;
import ext.mods.gameserver.model.item.instance.ItemInstance;
import ext.mods.gameserver.model.records.ManufactureItem;
import ext.mods.gameserver.model.trade.TradeItem;
import ext.mods.gameserver.model.trade.TradeList;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.PrivateStoreManageListBuy;
import ext.mods.gameserver.network.serverpackets.PrivateStoreManageListSell;
import ext.mods.gameserver.network.serverpackets.RecipeShopManageList;
import ext.mods.gameserver.network.serverpackets.UserInfo;
import ext.mods.gameserver.taskmanager.AttackStanceTaskManager;

/**
 * Private buy/sell/manufacture store for a {@link Player}.
 * OperateType remains owned by {@link Player}; this component drives store-specific lists and flows.
 * Offline store list persistence (onda 8) uses CachedData keys selllist/buylist/manufacturelist.
 */
public final class PlayerPrivateStore
{
	private final Player _owner;
	
	private final TradeList _buyList;
	private final TradeList _sellList;
	private final ManufactureList _manufactureList = new ManufactureList();
	
	private PrivateStoreType _privateStoreType = PrivateStoreType.NONE;
	
	/** Lazy: PlayerPrivateStore is constructed before Player's CachedData field. */
	private CachedDataValueString _sellStoreList;
	private CachedDataValueString _sellStoreName;
	private CachedDataValueString _buyStoreList;
	private CachedDataValueString _buyStoreName;
	private CachedDataValueString _manufactureStoreList;
	private CachedDataValueString _manufactureStoreName;
	
	public PlayerPrivateStore(Player owner)
	{
		_owner = owner;
		_buyList = new TradeList(owner);
		_sellList = new TradeList(owner);
	}
	
	/**
	 * Register offline-store CachedData keys. Must run before {@link ext.mods.commons.cached.CachedData#load()}
	 * so values hydrate on character restore.
	 */
	public void ensureStoreCaches()
	{
		if (_sellStoreList != null)
			return;
		
		final var cached = _owner.getCachedData();
		_sellStoreList = cached.newString("selllist");
		_sellStoreName = cached.newString("sellname");
		_buyStoreList = cached.newString("buylist");
		_buyStoreName = cached.newString("buyname");
		_manufactureStoreList = cached.newString("manufacturelist");
		_manufactureStoreName = cached.newString("manufacturename");
	}
	
	public TradeList getBuyList()
	{
		return _buyList;
	}
	
	public TradeList getSellList()
	{
		return _sellList;
	}
	
	public ManufactureList getManufactureList()
	{
		return _manufactureList;
	}
	
	public PrivateStoreType getPrivateStoreType()
	{
		return _privateStoreType;
	}
	
	public void setPrivateStoreType(PrivateStoreType type)
	{
		if (_privateStoreType == type)
			return;
		
		_privateStoreType = type;
		
		_owner.sendPacket(new UserInfo(_owner));
		
		switch (type)
		{
			case NONE:
				_owner.setOperateType(OperateType.NONE);
				break;
			case SELL:
				_owner.setOperateType(OperateType.SELL);
				break;
			case PACKAGE_SELL:
				_owner.setOperateType(OperateType.PACKAGE_SELL);
				break;
			case BUY:
				_owner.setOperateType(OperateType.BUY);
				break;
			case MANUFACTURE:
				_owner.setOperateType(OperateType.MANUFACTURE);
				break;
			default:
				_owner.setOperateType(OperateType.NONE);
				break;
		}
		_owner.broadcastUserInfo();
	}
	
	public boolean isInStoreMode()
	{
		final OperateType op = _owner.getOperateType();
		return op == OperateType.BUY || op == OperateType.SELL || op == OperateType.PACKAGE_SELL || op == OperateType.MANUFACTURE;
	}
	
	public boolean isInManageStoreMode()
	{
		final OperateType op = _owner.getOperateType();
		return op == OperateType.BUY_MANAGE || op == OperateType.SELL_MANAGE || op == OperateType.MANUFACTURE_MANAGE;
	}
	
	/**
	 * @param cancelActiveTrade if true, active peer trade is also canceled
	 * @return true if all conditions are met
	 */
	public boolean canOpenPrivateStore(boolean cancelActiveTrade)
	{
		if (isInStoreMode())
			return !_owner.isAlikeDead();
		
		if (cancelActiveTrade && _owner.getTrade().hasActiveTrade())
			_owner.getTrade().cancelActiveTrade();
		
		if (_owner.isInDuel() || AttackStanceTaskManager.getInstance().isInAttackStance(_owner) || _owner.getPvpFlag() > 0 || _owner.getAttack().isAttackingNow())
		{
			_owner.setOperateType(OperateType.NONE);
			_owner.sendPacket(SystemMessageId.CANT_OPERATE_PRIVATE_STORE_DURING_COMBAT);
			return false;
		}
		
		if (_owner.getCast().isCastingNow())
		{
			_owner.setOperateType(OperateType.NONE);
			_owner.sendPacket(SystemMessageId.PRIVATE_STORE_NOT_WHILE_CASTING);
			return false;
		}
		
		if (_owner.isInsideZone(ZoneId.NO_STORE) || _owner.isInOlympiadMode())
		{
			_owner.setOperateType(OperateType.NONE);
			_owner.sendPacket(SystemMessageId.NO_PRIVATE_STORE_HERE);
			return false;
		}
		
		if (_owner.isAlikeDead() || _owner.isMounted() || _owner.isProcessingRequest() || _owner.isOutOfControl())
		{
			_owner.setOperateType(OperateType.NONE);
			return false;
		}
		
		if ((_owner.isSitting() && !isInStoreMode()) || _owner.isStandingNow())
		{
			_owner.setOperateType(OperateType.NONE);
			return false;
		}
		
		return true;
	}
	
	public void tryOpenPrivateBuyStore()
	{
		if (!canOpenPrivateStore(true))
			return;
		
		if (_owner.getOperateType() == OperateType.NONE || _owner.getOperateType() == OperateType.BUY)
		{
			if (_owner.isSittingNow())
				return;
			
			if (_owner.getOperateType() == OperateType.BUY)
				_owner.standUp();
			
			_owner.getMove().stop();
			
			_owner.setOperateType(OperateType.BUY_MANAGE);
			_owner.sendPacket(new PrivateStoreManageListBuy(_owner));
		}
	}
	
	public void tryOpenPrivateSellStore(boolean isPackageSale)
	{
		if (!canOpenPrivateStore(true))
			return;
		
		if (_owner.getOperateType() == OperateType.NONE || _owner.getOperateType() == OperateType.SELL || _owner.getOperateType() == OperateType.PACKAGE_SELL)
		{
			if (_owner.isSittingNow())
				return;
			
			if (_owner.getOperateType() == OperateType.SELL || _owner.getOperateType() == OperateType.PACKAGE_SELL)
				_owner.standUp();
			
			_owner.getMove().stop();
			
			_owner.setOperateType(OperateType.SELL_MANAGE);
			_owner.sendPacket(new PrivateStoreManageListSell(_owner, isPackageSale));
		}
	}
	
	public void tryOpenWorkshop(boolean isDwarven)
	{
		if (!canOpenPrivateStore(true))
			return;
		
		if (_owner.getOperateType() == OperateType.NONE || _owner.getOperateType() == OperateType.MANUFACTURE)
		{
			if (_owner.isSittingNow())
				return;
			
			if (_owner.getOperateType() == OperateType.MANUFACTURE)
				_owner.standUp();
			
			_owner.setOperateType(OperateType.MANUFACTURE_MANAGE);
			_owner.sendPacket(new RecipeShopManageList(_owner, isDwarven));
		}
	}
	
	/** Offline-shop disconnect when store finishes (mirrors prior setOperateType side-effect for NONE). */
	public void onOperateTypeChanged(OperateType type)
	{
		if (ConfigOfflineShop.OFFLINE_DISCONNECT_FINISHED && type == OperateType.NONE
			&& (_owner.getClient() == null || _owner.getClient().isDetached()))
			_owner.logout(true);
	}
	
	/** Persist buy/sell/manufacture lists into CachedData (offline shop restore). */
	public void saveTradeList()
	{
		ensureStoreCaches();
		saveList(_buyList, _buyStoreList, _buyStoreName, true);
		saveList(_sellList, _sellStoreList, _sellStoreName, false);
		saveManufactureList(_manufactureList, _manufactureStoreList, _manufactureStoreName);
	}
	
	/** Restore buy/sell/manufacture lists from CachedData. */
	public void restoreStoreList()
	{
		ensureStoreCaches();
		addItemsToList(_sellStoreList.get(), _sellList, true);
		addItemsToList(_buyStoreList.get(), _buyList, false);
		addManufacturedItems(_manufactureStoreList.get());
	}
	
	private void saveList(TradeList list, CachedDataValueString storeList, CachedDataValueString storeName, boolean buyList)
	{
		StringBuilder tradeListBuilder = new StringBuilder();
		
		if (storeList == null)
			return;
		
		if (!list.isEmpty())
		{
			for (TradeItem item : list)
			{
				if (buyList)
					tradeListBuilder.append(item.getItem().getItemId()).append(",").append(item.getCount()).append(",").append(item.getPrice()).append(";");
				else
					tradeListBuilder.append(item.getObjectId()).append(",").append(item.getCount()).append(",").append(item.getPrice()).append(";");
			}
			
			storeList.set(tradeListBuilder.toString());
			tradeListBuilder.setLength(0);
			
			if (!list.getTitle().isEmpty())
				storeName.set(list.getTitle());
		}
	}
	
	private void saveManufactureList(ManufactureList list, CachedDataValueString storeList, CachedDataValueString storeName)
	{
		StringBuilder tradeListBuilder = new StringBuilder();
		
		final List<ManufactureItem> manufactureItemsList = list;
		if (storeList == null)
			return;
		
		if (!manufactureItemsList.isEmpty())
		{
			for (ManufactureItem item : manufactureItemsList)
				tradeListBuilder.append(item.recipeId()).append(",").append(item.cost()).append(";");
			
			storeList.set(tradeListBuilder.toString());
			tradeListBuilder.setLength(0);
			
			if (!list.getStoreName().isEmpty())
				storeName.set(list.getStoreName());
		}
	}
	
	private void addItemsToList(String storeList, List<TradeItem> list, boolean isSellList)
	{
		if (storeList == null)
			return;
		
		String[] items = storeList.split(";");
		for (String item : items)
		{
			if (item.isEmpty())
				continue;
			
			String[] values = item.split(",");
			if (values.length < 3)
				continue;
			
			int id = Integer.parseInt(values[0]);
			int count = Integer.parseInt(values[1]);
			int price = Integer.parseInt(values[2]);
			
			ItemInstance itemInstance = (isSellList) ? _owner.getInventory().getItemByObjectId(id) : _owner.getInventory().getItemByItemId(id);
			
			if (itemInstance == null || count < 1)
				continue;
			
			if (isSellList && count > itemInstance.getCount())
				count = itemInstance.getCount();
			
			list.add(new TradeItem(itemInstance, count, price));
		}
		
		if (isSellList)
			_sellList.setTitle(_sellStoreName.get());
		else
			_buyList.setTitle(_buyStoreName.get());
	}
	
	private void addManufacturedItems(String storeList)
	{
		if (storeList == null)
			return;
		
		String[] items = storeList.split(";");
		for (String item : items)
		{
			if (item.isEmpty())
				continue;
			
			String[] values = item.split(",");
			if (values.length < 2)
				continue;
			
			int recipeId = Integer.parseInt(values[0]);
			int price = Integer.parseInt(values[1]);
			_manufactureList.add(new ManufactureItem(recipeId, price));
		}
		
		_manufactureList.setStoreName(_manufactureStoreName.get());
	}
}

