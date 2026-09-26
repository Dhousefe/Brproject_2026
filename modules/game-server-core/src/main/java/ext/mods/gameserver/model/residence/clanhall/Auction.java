/*
* Copyleft © 2024-2026 L2Brproject
* * This file is part of L2Brproject derived from aCis409/RusaCis3.8
* * L2Brproject is free software: you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation, either version 3 of the License.
* * L2Brproject is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* General Public License for more details.
* * You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
* Our main Developers, Dhousefe-L2JBR, Agazes33, Ban-L2jDev, Warman, SrEli.
* Our special thanks, Nattan Felipe, Diego Fonseca, Junin, ColdPlay, Denky, MecBew, Localhost, MundvayneHELLBOY, 
* SonecaL2, Eduardo.SilvaL2J, biLL, xpower, xTech, kakuzo, Tiagorosendo, Schuster, LucasStark, damedd
* as a contribution for the forum L2JBrasil.com
 */
package ext.mods.gameserver.model.residence.clanhall;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import ext.mods.commons.lang.StringUtil;
import ext.mods.commons.logging.CLogger;
import ext.mods.commons.pool.ThreadPool;

import ext.mods.gameserver.data.adapter.JdbcClanHallAuctionStore;
import ext.mods.gameserver.data.repository.ClanHallAuctionStore;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.gameserver.model.pledge.Clan;
import ext.mods.gameserver.network.SystemMessageId;
import ext.mods.gameserver.network.serverpackets.SystemMessage;

/**
 * An Auction container, used in conjonction with {@link ClanHall} system.<br>
 * <br>
 * <b>Purchasing Clan Halls</b><br>
 * <ul>
 * <li>The clan leader who placed the highest bid at the end of the auction wins the clan hall. Players cannot see how much others have bid.</li>
 * <li>Any player can view the clan halls in the auction, even if they are not qualified to make a bid.</li>
 * <li>When a clan leader places a bid on a clan hall, the bid amount of adena is taken from the clan's warehouse. Bids must be higher than the previous bid. Clan leaders cannot bid more than the amount of adena in the clan's warehouse.</li>
 * <li>Bids can be changed once they have been made by pressing the Rebid button, as long as the new bid is higher than the original.</li>
 * <li>If a bid is cancelled, the adena is returned, minus a ten percent tax fee.</li>
 * <li>If a clan disbands after placing a bid, the amount of the bid disappears.</li>
 * <li>If a clan acquires another clan hall while bidding, the bid is canceled automatically and the adena is returned to the clan's warehouse, minus taxes.</li>
 * <li>If two different clans placed the highest bid at the end of an auction, the clan hall is sold to the clan that bid first.</li>
 * <li>If a clan is successful in purchasing a clan hall, the clan leader receives a message that they won. Any previous residents that are still in the clan hall are kicked out.</li>
 * <li>The bid amounts of the clan leaders who bid unsuccessfully are returned to their clan's warehouses.</li>
 * </ul>
 * <b>Selling Clan Halls</b><br>
 * <ul>
 * <li>Clan leaders may put their clan hall up for auction through the clan hall manager.</li>
 * <li>Auction periods can be set for seven days, three days or one day. For example, if a three-day auction were set on Nov. 13 at 7:27pm, the end would be November 16 at 8:00pm.</li>
 * <li>Clan leaders can write a simple description of the clan hall they wish to auction.</li>
 * <li>Clan leaders must pay a deposit to put their clan hall up for auction.</li>
 * <li>The clan leader can cancel the auction during the set time, but the deposit is not returned and they cannot set up another auction for seven days.</li>
 * <li>When a clan hall does not have an owner, or has not been maintained sufficiently, it will be set up automatically for a seven-day auction period.</li>
 * <li>If the clan breaks up before the end of an auction period for their clan hall, the auction will continue, but the proceeds and deposit from selling the clan hall cannot be received.</li>
 * <li>If no one participates in an auction, the clan hall is returned to the owners, but their deposit is not returned.</li>
 * <li>If a clan successfully sells a clan hall, the clan leader will receive a message. The highest bid, minus taxes, is placed in the selling clan's warehouse, along with the deposit.</li>
 * </ul>
 */
public class Auction
{
	private static final CLogger LOGGER = new CLogger(Auction.class.getName());
	
	private final Map<Integer, Bidder> _bidders = new ConcurrentHashMap<>();
	private final ClanHall _ch;
	private final ClanHallAuctionStore _store;
	
	private long _endDate;
	
	private Bidder _highestBidder;
	private Seller _seller;
	
	private Future<?> _task;
	
	public Auction(ClanHall ch, int sellerBid, String sellerName, String sellerClanName, long endDate)
	{
		this(ch, sellerBid, sellerName, sellerClanName, endDate, new JdbcClanHallAuctionStore());
	}

	Auction(ClanHall ch, int sellerBid, String sellerName, String sellerClanName, long endDate, ClanHallAuctionStore store)
	{
		_ch = ch;
		_store = store;
		_endDate = endDate;
		
		if (!StringUtil.isEmpty(sellerName, sellerClanName))
			_seller = new Seller(sellerName, sellerClanName, sellerBid);
		
		try
		{
			for (ClanHallAuctionStore.BidRecord record : _store.loadBids(ch.getId()))
			{
				final Bidder bidder = new Bidder(record.bidderName(), record.clanName(), record.bid(), record.bidTime());
				if (_highestBidder == null)
					_highestBidder = bidder;
				_bidders.put(record.clanId(), bidder);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load Auction bid.", e);
		}
		
		startAutoTask();
	}
	
	public final long getEndDate()
	{
		return _endDate;
	}
	
	public final void setEndDate(long endDate)
	{
		_endDate = System.currentTimeMillis() + endDate;
	}
	
	public final Bidder getHighestBidder()
	{
		return _highestBidder;
	}
	
	public final Seller getSeller()
	{
		return _seller;
	}
	
	public final void setSeller(Clan clan, int bid)
	{
		if (clan == null)
			return;
		
		_seller = new Seller(clan.getLeaderName(), clan.getName(), bid);
	}
	
	public final Map<Integer, Bidder> getBidders()
	{
		return _bidders;
	}
	
	/**
	 * Test the auction process.<br>
	 * <br>
	 * If the end date already exceeded, add 1 week to the time and save it on database, otherwise, schedule a task to fire the auction ending process.
	 */
	public void startAutoTask()
	{
		long currentTime = System.currentTimeMillis();
		long taskDelay = 0;
		
		if (_endDate <= currentTime)
		{
			_endDate = currentTime + 604800000;
			
			_store.updateEndDate(_ch.getId(), _endDate);
		}
		else
			taskDelay = _endDate - currentTime;
		
		_task = ThreadPool.schedule(this::endAuction, taskDelay);
	}
	
	/**
	 * Set a bid for the given {@link Auction}.
	 * @param player : The {@link Player} who requested the bid.
	 * @param bid : The bid amount.
	 */
	public void setBid(Player player, int bid)
	{
		final Clan clan = player.getClan();
		if (clan == null)
			return;
		
		if (bid <= getMinimumBid())
		{
			player.sendPacket(SystemMessageId.BID_PRICE_MUST_BE_HIGHER);
			return;
		}
		
		int requiredAdena = bid;
		
		Bidder bidder = _bidders.get(player.getClanId());
		
		if (bidder != null)
		{
			if (bid <= bidder.getBid())
			{
				player.sendPacket(SystemMessageId.BID_PRICE_MUST_BE_HIGHER);
				return;
			}
			
			requiredAdena -= bidder.getBid();
		}
		
		if (!takeItem(player, requiredAdena))
			return;
		
		final long time = System.currentTimeMillis();
		
		if (bidder == null)
		{
			bidder = new Bidder(clan.getLeaderName(), clan.getName(), bid, time);
			
			_bidders.put(player.getClanId(), bidder);
		}
		else
		{
			bidder.setBid(bid);
			bidder.setTime(time);
		}
		
		recalculateHighestBidder();
		
		player.sendPacket(SystemMessageId.BID_IN_CLANHALL_AUCTION);
		
		clan.setAuctionBiddedAt(_ch.getId());
		
		_store.saveBid(new ClanHallAuctionStore.BidRecord(_ch.getId(), player.getName(), player.getClanId(), clan.getName(), bid, time));
	}
	
	/**
	 * Return bids into WHC.
	 * @param clan : The {@link Clan} to make warehouse checks on.
	 * @param quantity : The amount of returned Adena.
	 * @param penalty : If true, 10% of quantity is lost.
	 */
	private static void returnItem(Clan clan, int quantity, boolean penalty)
	{
		if (clan == null)
			return;
		
		if (penalty)
			quantity *= 0.9;
		
		final int limit = Integer.MAX_VALUE - clan.getWarehouse().getAdena();
		quantity = Math.min(quantity, limit);
		
		clan.getWarehouse().addItem(57, quantity);
	}
	
	/**
	 * Take Adena from Clan warehouse of the {@link Player} set as parameter. This method is used for the Auction confimation, holding lease.
	 * @param bidder : The {@link Player} to make checks on.
	 * @param quantity : The amount of Adena.
	 * @return True if the operation is successful, or false otherwise.
	 */
	public boolean takeItem(Player bidder, int quantity)
	{
		final Clan clan = bidder.getClan();
		if (clan == null)
			return false;
		
		if (clan.getWarehouse().getAdena() < quantity)
		{
			bidder.sendPacket(SystemMessageId.NOT_ENOUGH_ADENA_IN_CWH);
			return false;
		}
		
		clan.getWarehouse().destroyItemByItemId(57, quantity);
		return true;
	}
	
	/**
	 * Remove bids.
	 * @param newOwner : The {@link Clan} who won the bid.
	 */
	public void removeBids(Clan newOwner)
	{
		_store.deleteBids(_ch.getId());
		
		for (Bidder bidder : _bidders.values())
		{
			final Clan clan = bidder.getClan();
			if (clan == null)
				continue;
			
			clan.setAuctionBiddedAt(0);
			
			if (clan != newOwner)
				returnItem(clan, bidder.getBid(), true);
				
			if (newOwner != null)
				clan.broadcastToMembers(SystemMessage.getSystemMessage(SystemMessageId.CLANHALL_AWARDED_TO_CLAN_S1).addString(newOwner.getName()));
		}
		_bidders.clear();
	}
	
	/** End of auction */
	public void endAuction()
	{
		if (_task != null)
		{
			_task.cancel(false);
			_task = null;
		}
		
		if (_highestBidder == null)
		{
			if (_seller == null)
				startAutoTask();
			else
			{
				final Clan owner = _seller.getClan();
				if (owner == null)
					return;
				
				owner.broadcastToMembers(SystemMessage.getSystemMessage(SystemMessageId.CLANHALL_NOT_SOLD));
			}
			return;
		}
		
		if (_seller != null)
		{
			final Clan clan = _seller.getClan();
			
			returnItem(clan, _highestBidder.getBid(), true);
			returnItem(clan, _ch.getLease(), false);
		}
		
		_ch.setOwner(_highestBidder.getClan());
	}
	
	/**
	 * Cancel the bid placed by a {@link Clan}.
	 * @param clan : The {@link Clan} related to the bidder.
	 */
	public void cancelBid(Clan clan)
	{
		if (clan == null)
			return;
		
		final Bidder bidder = _bidders.remove(clan.getClanId());
		if (bidder == null)
			return;
		
		_store.deleteBid(_ch.getId(), clan.getClanId());
		
		returnItem(clan, bidder.getBid(), true);
		
		clan.setAuctionBiddedAt(0);
		
		if (bidder == _highestBidder)
			recalculateHighestBidder();
	}
	
	/** Cancel auction */
	public void cancelAuction()
	{
		if (_seller == null)
			return;
		
		removeBids(_seller.getClan());
		
		reset(false);
		
		_ch.updateDb();
	}
	
	/** Confirm an auction */
	public void confirmAuction()
	{
		if (_seller == null)
			return;
		
		_store.updateSeller(new ClanHallAuctionStore.SellerRecord(_ch.getId(), _seller.getBid(), _seller.getName(), _seller.getClanName(), _endDate));
	}
	
	public void recalculateHighestBidder()
	{
		Bidder highestBidder = null;
		int highestBid = 0;
		
		for (Bidder bidder : _bidders.values())
		{
			if (bidder.getBid() > highestBid)
			{
				highestBidder = bidder;
				
				highestBid = bidder.getBid();
			}
		}
		
		_highestBidder = highestBidder;
	}
	
	/**
	 * Reset all variables of this {@link Auction}. It has to be used with {@link ClanHall#updateDb()}.
	 * @param runTask : If true, we also care about stopping and renew the auto task.
	 */
	public void reset(boolean runTask)
	{
		_highestBidder = null;
		_seller = null;
		
		_endDate = 0;
		
		if (_task != null)
		{
			_task.cancel(false);
			_task = null;
		}
		
		if (runTask)
			startAutoTask();
	}
	
	public int getMinimumBid()
	{
		return (_seller == null) ? _ch.getAuctionMin() : Math.max(_ch.getAuctionMin(), _seller.getBid());
	}
}
