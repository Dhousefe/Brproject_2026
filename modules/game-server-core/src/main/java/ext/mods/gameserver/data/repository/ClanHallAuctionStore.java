package ext.mods.gameserver.data.repository;

import java.util.List;

/** Persistence port for Clan Hall auction bids and seller state. */
public interface ClanHallAuctionStore
{
	List<BidRecord> loadBids(int clanHallId);

	void updateEndDate(int clanHallId, long endDate);

	void deleteBids(int clanHallId);

	void deleteBid(int clanHallId, int clanId);

	void saveBid(BidRecord bid);

	void updateSeller(SellerRecord seller);

	record BidRecord(int clanHallId, String bidderName, int clanId, String clanName, int bid, long bidTime)
	{
	}

	record SellerRecord(int clanHallId, int bid, String sellerName, String sellerClanName, long endDate)
	{
	}
}
