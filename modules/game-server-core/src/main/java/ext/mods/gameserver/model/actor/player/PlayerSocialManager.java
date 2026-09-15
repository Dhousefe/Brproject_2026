package ext.mods.gameserver.model.actor.player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ext.mods.gameserver.data.manager.CoupleManager;
import ext.mods.gameserver.enums.PrivateStoreType;
import ext.mods.gameserver.model.World;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.actor.instance.WeddingManagerNpc;
import ext.mods.gameserver.model.craft.ManufactureList;
import ext.mods.gameserver.model.trade.TradeList;
import ext.mods.gameserver.network.serverpackets.EtcStatusUpdate;

/**
 * Social surface for a {@link Player}: peer-to-peer trade, private buy/sell/manufacture store,
 * crafting, marriage / engagement and friend / block lists.<br>
 * Aggregates {@link PlayerTrade}, {@link PlayerPrivateStore} and the friend/block + couple + marry
 * request state that previously lived directly on {@link Player} / {@link PlayerSocial}.
 */
public final class PlayerSocialManager
{
	private final Player _owner;
	private final PlayerTrade _trade;
	private final PlayerPrivateStore _privateStore;

	// Marry / engagement
	private boolean _isUnderMarryRequest;
	private int _requesterId;

	// Couple id (paired character link)
	private int _coupleId;

	// Friend / block lists
	private boolean _isBlockingAll;
	private final Set<Integer> _selectedBlocksList = ConcurrentHashMap.newKeySet();
	private final Set<Integer> _selectedFriendList = ConcurrentHashMap.newKeySet();

	public PlayerSocialManager(Player owner)
	{
		_owner = owner;
		_trade = new PlayerTrade(owner);
		_privateStore = new PlayerPrivateStore(owner);
	}

	// ============================================================
	// Peer-to-peer trade
	// ============================================================

	public TradeList getActiveTradeList()
	{
		return _trade.getActiveTradeList();
	}

	public void setActiveTradeList(TradeList tradeList)
	{
		_trade.setActiveTradeList(tradeList);
	}

	public void onTradeStart(Player partner)
	{
		_trade.onTradeStart(partner);
	}

	public void onTradeConfirm(Player partner)
	{
		_trade.onTradeConfirm(partner);
	}

	public void onTradeCancel(Player partner)
	{
		_trade.onTradeCancel(partner);
	}

	public void onTradeFinish(boolean isSuccessful)
	{
		_trade.onTradeFinish(isSuccessful);
	}

	public void startTrade(Player partner)
	{
		_trade.startTrade(partner);
	}

	public void cancelActiveTrade()
	{
		_trade.cancelActiveTrade();
	}

	// ============================================================
	// Private buy / sell / manufacture store
	// ============================================================

	public boolean canOpenPrivateStore(boolean cancelActiveTrade)
	{
		return _privateStore.canOpenPrivateStore(cancelActiveTrade);
	}

	public void tryOpenPrivateBuyStore()
	{
		_privateStore.tryOpenPrivateBuyStore();
	}

	public void tryOpenPrivateSellStore(boolean isPackageSale)
	{
		_privateStore.tryOpenPrivateSellStore(isPackageSale);
	}

	public void tryOpenWorkshop(boolean isDwarven)
	{
		_privateStore.tryOpenWorkshop(isDwarven);
	}

	public TradeList getBuyList()
	{
		return _privateStore.getBuyList();
	}

	public TradeList getSellList()
	{
		return _privateStore.getSellList();
	}

	public ManufactureList getManufactureList()
	{
		return _privateStore.getManufactureList();
	}

	public boolean isInStoreMode()
	{
		return _privateStore.isInStoreMode();
	}

	public boolean isInManageStoreMode()
	{
		return _privateStore.isInManageStoreMode();
	}

	public PrivateStoreType getPrivateStoreType()
	{
		return _privateStore.getPrivateStoreType();
	}

	public void setPrivateStoreType(PrivateStoreType type)
	{
		_privateStore.setPrivateStoreType(type);
	}

	public void saveTradeList()
	{
		_privateStore.saveTradeList();
	}

	public void restoreStoreList()
	{
		_privateStore.restoreStoreList();
	}

	// ============================================================
	// Marriage / engagement
	// ============================================================

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

	public int getCoupleId()
	{
		return _coupleId;
	}

	public void setCoupleId(int coupleId)
	{
		_coupleId = coupleId;
	}

	public void engageAnswer(int answer)
	{
		if (!isUnderMarryRequest() || getRequesterId() == 0)
			return;

		final Player requester = World.getInstance().getPlayer(getRequesterId());
		if (requester != null)
		{
			if (answer == 1)
			{
				CoupleManager.getInstance().addCouple(requester, _owner);
				WeddingManagerNpc.justMarried(requester, _owner);
			}
			else
			{
				setUnderMarryRequest(false);
				_owner.sendMessage(_owner.getSysString(10_012));

				requester.setUnderMarryRequest(false);
				requester.sendMessage(_owner.getSysString(10_013));
			}
		}
	}

	// ============================================================
	// Friend / block lists
	// ============================================================

	public boolean isBlockingAll()
	{
		return _isBlockingAll;
	}

	public void setInBlockingAll(boolean isBlockingAll)
	{
		_isBlockingAll = isBlockingAll;
		_owner.sendPacket(new EtcStatusUpdate(_owner));
	}

	public void selectFriend(int friendId)
	{
		_selectedFriendList.add(friendId);
	}

	public void deselectFriend(int friendId)
	{
		_selectedFriendList.remove(friendId);
	}

	public Set<Integer> getSelectedFriendList()
	{
		return _selectedFriendList;
	}

	public void selectBlock(int friendId)
	{
		_selectedBlocksList.add(friendId);
	}

	public void deselectBlock(int friendId)
	{
		_selectedBlocksList.remove(friendId);
	}

	public Set<Integer> getSelectedBlocksList()
	{
		return _selectedBlocksList;
	}
}